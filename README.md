# Bookstore Backend API

A Java/Spring Boot backend for an online bookstore, built as a technical assignment. The focus is on clear business rules, transactional consistency, concurrency, and asynchronous order processing.

## Tech stack and architecture

- **Java 21**, **Spring Boot 4.1.2**, Spring Security (JWT), Maven
- **PostgreSQL 17**, **Flyway**, Spring JDBC (`NamedParameterJdbcTemplate`)
- **Apache Kafka**, Docker Compose, JUnit/Mockito (initial tests)
- **Lightweight hexagonal architecture**, organized by feature (`auth`, `user`, `category`, `book`, `cart`, `order`, `inventory`, `idempotency`, `outbox`, `messaging`):
  - `api`  -  REST controllers and request/response DTOs
  - `application`  -  business logic; `application.port`  -  repository interfaces
  - `domain`  -  business models and enums
  - `infrastructure`  -  JDBC implementations, Kafka publisher/consumer

Persistence uses explicit SQL rather than JPA entities. Flyway migrations (`V1`–`V6`) define the database schema.

## Business logic

- **Authentication:** Customers register/login with a short-lived JWT. Public users can browse books/categories; administrative writes require the `ADMIN` role.
- **Books and categories:** Books have unique ISBNs, prices, stock, and multiple categories. Books can be archived/restored to preserve order history. Listing supports filters and pagination.
- **Cart:** One cart per user, created on first add. Adding/removing a book changes its quantity by **one**; it does not reserve stock. Archived books already in a cart are removed lazily when the cart is read. Checkout clears cart items but keeps the cart.
- **Checkout:** An authenticated customer can submit a non-empty cart even when stock is insufficient. The API immediately creates a `PENDING` order; each `order_item` stores the quantity and the **price at purchase** (`unit_price`). Available inventory is allocated later.
- **Orders:** Customers see their own orders. Administrators can list all orders with pagination. Order-item allocation tracks `allocated_quantity` and `backordered_quantity`.

### Order submission and Kafka

```text
POST /api/orders
  -> One PostgreSQL transaction:
     create PENDING order + order_items + ORDER_SUBMITTED outbox row + clear cart
  -> OutboxPublisher claims events and publishes to bookstore.orders.submitted
  -> OrderSubmittedConsumer receives the event
  -> OrderProcessingService deduplicates and allocates inventory
  -> Order status becomes CONFIRMED or BACKORDERED (or remains PENDING while unclassified)
```

The **transactional outbox** avoids committing an order without recording the event to publish. Publishers use `FOR UPDATE SKIP LOCKED`, claim leases, and a `claim_token` to avoid stale workers updating a newer claim. Failed sends are retried. Publishing is **at least once**, so Kafka messages may be duplicated.

The consumer uses the `processed_kafka_events` table: inserting the event marker and updating inventory/orders are part of the **same database transaction**. Kafka is acknowledged only after processing succeeds. This protects database effects from duplicate delivery of the *same event*.

### Inventory, backorders, and restocking

- Inventory allocation locks each book row (`SELECT ... FOR UPDATE`) and processes **outstanding orders oldest first**, including older `PENDING` orders whose Kafka events have not yet been handled.
- An order may be partially allocated; any remaining quantity becomes backordered. Available `books.stock` is never intentionally allowed to go below zero.
- **Admin restock** (`POST /api/books/{bookId}/restock`) increments stock and immediately invokes the **same allocation service in one transaction**. Existing outstanding demand is fulfilled before leftover stock is made available for later orders.
- Restocking is currently **synchronous**. `BOOK_RESTOCKED` and an inventory topic are defined, but publishing a restock event is not implemented yet.

The current per-book locking and oldest-first query cover the main workflow, but **multi-book deadlocks, concurrent status updates, and strict ordering across uncommitted transactions still need hardening**.

### HTTP idempotency

`POST /api/books`, `POST /api/orders`, and the restock endpoint require an `Idempotency-Key` header. The database uniquely identifies a request by **(user ID, operation, key)**:

- **Same key + same request hash:** replay the original HTTP status/body without repeating the operation.
- **Same key + changed payload:** return `409 Conflict`.
- **New key:** treat as a new operation (subject to business constraints such as unique ISBN).

The claim, business action, and saved response commit in **one transaction**. Records expire after 24 hours and are cleaned up periodically. Cart add/remove intentionally do **not** use idempotency keys; database operations protect against lost quantity updates, but a repeated client request can still change quantity twice.

## Run locally

**Requirements:** JDK 21, Docker Desktop / Docker Compose, and an available port **5432**, **9092**, and **8080**. Commands below use Windows PowerShell.

1. From the project root, set `DB_PASSWORD` to a local password and generate a Base64-encoded 32-byte `JWT_SECRET`. Run the remaining commands from the same PowerShell session so Docker Compose and Spring Boot receive the environment variables.

   ```powershell
   $env:DB_PASSWORD = Read-Host 'PostgreSQL password'
   $bytes = New-Object byte[] 32
   $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
   $rng.GetBytes($bytes)
   $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
   $rng.Dispose()
   ```

   If PostgreSQL already has a data volume, enter the current password for the `bookstore` role. Changing `DB_PASSWORD` only sets the database password when PostgreSQL initializes a fresh volume.

2. Start PostgreSQL and Kafka:

   ```powershell
   docker compose up -d
   ```

3. Create the Kafka topic:

   ```powershell
   docker exec bookstore-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic bookstore.orders.submitted --partitions 3 --replication-factor 1
   ```

4. Start Spring Boot:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   The API runs at **http://localhost:8080**. Flyway applies database migrations at startup.

5. Register/login with `/api/auth/register` and `/api/auth/login`. Send the access token as `Authorization: Bearer <token>`. All newly registered users have the `USER` role. To try admin endpoints **locally only**, register an account, then promote it in the local database and log in again:

   ```powershell
   docker exec bookstore-postgres psql -U bookstore -d bookstore -c "UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';"
   ```

   Replace the email with the account you registered. Example requests are in `requests/*.http` (IntelliJ HTTP Client).

## Main API routes

| Route | Purpose |
| --- | --- |
| `POST /api/auth/register`, `/api/auth/login` | Registration and JWT login |
| `GET /api/books`, `GET /api/categories` | Public catalog |
| `POST /api/books`, `PATCH/DELETE /api/books/{id}` | Admin book management (archive via DELETE) |
| `GET /api/cart`, `POST/DELETE /api/cart/items/{bookId}` | Cart and one-at-a-time quantity changes |
| `POST /api/orders` | Idempotent checkout |
| `GET /api/orders`, `GET /api/orders/{id}` | Customer order history |
| `GET /api/admin/orders` | Paginated admin order list |
| `POST /api/books/{bookId}/restock` | Idempotent admin restock |

## Known trade-offs and next steps

1. **Concurrency.** A PostgreSQL integration test verifies that two simultaneous creates with the same ISBN result in one committed book and one conflict. Multi-book inventory locking, retries after deadlocks, and allocation priority under concurrent updates still need coverage and hardening.
2. **Order lifecycle.** Add cancellation that releases allocated stock, plus shipping and delivery status transitions.
3. **Kafka resilience.** The outbox retries failed publishes. Define consumer retry and dead-letter handling, and implement restock notifications if they are needed.
4. **Observability.** Controllers log endpoint entry at `INFO`, and request validation failures are logged at `DEBUG`. Key service writes log after commit, and order submission and processing logs include order and event IDs. Request correlation across components, metrics, and health monitoring remain open.
5. **Testing.** Book and category services have unit tests. The duplicate-ISBN race runs against PostgreSQL 17 with Flyway and Testcontainers when Docker is available. Expand coverage for HTTP authorization, checkout idempotency, Kafka redelivery, and inventory concurrency.
6. **Deployment.** Persist Kafka broker data and automate topic creation and local startup.
7. **Migrations.** The V4 ISBN migration deletes existing book data. Use a backfill that preserves rows before applying this schema change to a database with data to retain.
8. **Security.** Local setup takes `DB_PASSWORD` and `JWT_SECRET` from the environment (.env file). Access tokens expire after 15 minutes by default. Logout relies on the client discarding the token, so a retained copy remains usable until expiry. Server-side revocation would require shared state across app instances.

**Scope:** This is an in-progress backend demonstration. The code illustrates the intended architecture and business flow; it is not yet production-hardened or fully covered by automated tests.

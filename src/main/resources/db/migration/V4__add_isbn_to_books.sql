-- Add ISBN column to books table
-- If books don't have ISBN -> no unique constraint.
-- => 2 admins can create the same book (because they have 2 different idempotency keys)
-- Solution 1) Introduce unique constraint (author, title), but 1 book can have multiple editions
-- Solution 2) Introduce ISBN column:
--      For 2 parallel request with different Idempotency key, both can check there is no existing book,
--      but 2nd request will fail on DB insert due to UNIQUE CONSTRAINT (check-then-insert race condition)
-- Note: We shouldn't lock the whole table because that will slow down the other requests, although this would fix the check-then-insert race condition
--      - SERIALIZABLE isolation level: @Transactional(isolation = Isolation.SERIALIZABLE)
--      - or Lock the table "LOCK TABLE books IN SHARE ROW EXCLUSIVE MODE;" at the beginning of the transaction

-- Clear existing book-category associations
DELETE FROM book_categories;

-- Clear existing books
DELETE FROM books;

-- Add mandatory ISBN-13 column (example: 9780441172719)
ALTER TABLE books ADD COLUMN isbn VARCHAR(13) NOT NULL;

-- Validate ISBN-13 format
ALTER TABLE books ADD CONSTRAINT chk_books_isbn_format CHECK (isbn ~ '^[0-9]{13}$');

-- Prevent duplicate ISBNs
ALTER TABLE books ADD CONSTRAINT uq_books_isbn UNIQUE (isbn);

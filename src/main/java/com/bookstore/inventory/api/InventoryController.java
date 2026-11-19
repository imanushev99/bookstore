
package com.bookstore.inventory.api;

import com.bookstore.common.security.SecurityUtils;
import com.bookstore.idempotency.application.IdempotencyService;
import com.bookstore.idempotency.application.RequestHasher;
import com.bookstore.idempotency.domain.IdempotencyOperation;
import com.bookstore.inventory.api.dto.RestockBookRequest;
import com.bookstore.inventory.application.InventoryService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final InventoryService inventoryService;
    private final IdempotencyService idempotencyService;
    private final RequestHasher requestHasher;

    public InventoryController(
            InventoryService inventoryService,
            IdempotencyService idempotencyService,
            RequestHasher requestHasher) {
        this.inventoryService = inventoryService;
        this.idempotencyService = idempotencyService;
        this.requestHasher = requestHasher;
    }

    // BEGIN TRANSACTION
    //
    // 1. Claim idempotency key
    // 2. Lock book row
    // 3. Increase stock
    // 4. Allocate inventory to outstanding orders
    // 5. Refresh affected order statuses
    // 6. Store original HTTP 204 response
    //
    // COMMIT
    @PostMapping("/{bookId}/restock")
    public ResponseEntity<?> restockBook(
            @PathVariable Long bookId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RestockBookRequest request) {
        log.info("action=restockBook, bookId={}, quantity={}", bookId, request.getQuantity());

        Long userId = SecurityUtils.getCurrentUserId();

        String requestHash = requestHasher.hash("POST /api/books/" + bookId + "/restocks", request);

        return idempotencyService.execute(
                userId,
                IdempotencyOperation.RESTOCK_BOOK,
                idempotencyKey,
                requestHash,
                () -> {
                    inventoryService.restockBook(bookId, request.getQuantity());
                    return ResponseEntity.noContent().build();
                }
        );
    }
}

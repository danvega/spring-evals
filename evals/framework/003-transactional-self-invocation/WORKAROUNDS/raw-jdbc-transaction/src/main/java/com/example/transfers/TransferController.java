package com.example.transfers;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TransferController {

    private final TransferService transferService;
    private final AccountRepository accountRepository;

    public TransferController(TransferService transferService, AccountRepository accountRepository) {
        this.transferService = transferService;
        this.accountRepository = accountRepository;
    }

    public record TransferRequest(String from, String to, BigDecimal amount) {
    }

    @PostMapping("/api/transfers")
    public Map<String, String> transfer(@RequestBody TransferRequest request) {
        transferService.transfer(request.from(), request.to(), request.amount());
        return Map.of("status", "completed");
    }

    @GetMapping("/api/accounts/{id}")
    public ResponseEntity<Map<String, Object>> account(@PathVariable String id) {
        return accountRepository.findById(id)
                .map(account -> ResponseEntity.ok(Map.<String, Object>of(
                        "id", account.getId(),
                        "owner", account.getOwner(),
                        "balance", account.getBalance())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(TransferException.class)
    public ResponseEntity<Map<String, String>> handleTransferException(TransferException exception) {
        return ResponseEntity.unprocessableEntity().body(Map.of("error", exception.getMessage()));
    }
}

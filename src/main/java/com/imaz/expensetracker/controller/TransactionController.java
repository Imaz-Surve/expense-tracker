package com.imaz.expensetracker.controller;

import com.imaz.expensetracker.dto.TransactionDto.*;
import com.imaz.expensetracker.entity.Transaction.Category;
import com.imaz.expensetracker.entity.Transaction.StatementType;
import com.imaz.expensetracker.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    // â”€â”€ Upload PDF Statement â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @PostMapping("/statements/upload")
    public ResponseEntity<UploadResponse> uploadStatement(
            @RequestParam("file") MultipartFile file,
            @RequestParam("statementType") StatementType statementType,
            @RequestParam(value = "monthYear", required = false) String monthYear) throws Exception {
        return ResponseEntity.ok(
                transactionService.uploadStatement(file, statementType, monthYear));
    }

    // â”€â”€ List All Uploaded Statements â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @GetMapping("/statements")
    public ResponseEntity<List<StatementResponse>> getStatements() {
        return ResponseEntity.ok(transactionService.getStatements());
    }

    // â”€â”€ Delete a Statement and Its Transactions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @DeleteMapping("/statements/{id}")
    public ResponseEntity<Void> deleteStatement(@PathVariable Long id) {
        transactionService.deleteStatement(id);
        return ResponseEntity.noContent().build();
    }

    // â”€â”€ Get Transactions (with optional filters) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Category category) {
        return ResponseEntity.ok(transactionService.getTransactions(from, to, category));
    }

    // â”€â”€ Dashboard / Analytics â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(transactionService.getDashboard());
    }
}

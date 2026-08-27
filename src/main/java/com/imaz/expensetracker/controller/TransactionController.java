package com.imaz.expensetracker.controller;

import com.imaz.expensetracker.dto.TransactionDto.*;
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

    // ── Parse PDF (preview only, does not persist) ────────────────────────────
    @PostMapping("/statements/parse")
    public ResponseEntity<ParseResponse> parseStatement(
            @RequestParam("file") MultipartFile file,
            @RequestParam("statementType") StatementType statementType,
            @RequestParam(value = "monthYear", required = false) String monthYear) throws Exception {
        return ResponseEntity.ok(transactionService.parseStatement(file, statementType, monthYear));
    }

    // ── Save reviewed transactions ────────────────────────────────────────────
    @PostMapping("/statements/save")
    public ResponseEntity<UploadResponse> saveStatement(@RequestBody SaveStatementRequest req) {
        return ResponseEntity.ok(transactionService.saveStatement(req));
    }

    // ── Statements listing / delete ───────────────────────────────────────────
    @GetMapping("/statements")
    public ResponseEntity<List<StatementResponse>> getStatements() {
        return ResponseEntity.ok(transactionService.getStatements());
    }

    @DeleteMapping("/statements/{id}")
    public ResponseEntity<Void> deleteStatement(@PathVariable Long id) {
        transactionService.deleteStatement(id);
        return ResponseEntity.noContent().build();
    }

    // ── Transactions ──────────────────────────────────────────────────────────
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(transactionService.getTransactions(from, to, categoryId));
    }

    @PatchMapping("/transactions/{id}")
    public ResponseEntity<TransactionResponse> updateCategory(
            @PathVariable Long id, @RequestBody UpdateCategoryRequest req) {
        return ResponseEntity.ok(transactionService.updateCategory(id, req.getCategoryId()));
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(transactionService.getDashboard());
    }
}

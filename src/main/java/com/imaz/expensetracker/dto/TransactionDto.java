package com.imaz.expensetracker.dto;

import com.imaz.expensetracker.entity.Transaction.StatementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class TransactionDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionResponse {
        private Long id;
        private String description;
        private String merchantName;
        private BigDecimal amount;
        private LocalDate transactionDate;
        private CategoryDto category;
        private StatementType statementType;
        private Boolean isDebit;
        private LocalDateTime createdAt;
        private Long statementId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySummary {
        private CategoryDto category;
        private BigDecimal total;
        private Long count;
        private double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlySummary {
        private String month;        // "2024-03"
        private BigDecimal total;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardResponse {
        private BigDecimal totalSpentThisMonth;
        private BigDecimal totalSpentAllTime;
        private List<CategorySummary> categoryBreakdown;
        private List<MonthlySummary> monthlyTrend;
        private List<TransactionResponse> recentTransactions;
        private Long totalTransactions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatementResponse {
        private Long id;
        private String fileName;
        private StatementType statementType;
        private String monthYear;
        private Integer transactionCount;
        private LocalDateTime uploadedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadResponse {
        private Long statementId;
        private String fileName;
        private int parsedCount;
        private String message;
    }

    // ── Category DTOs ─────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryDto {
        private Long id;
        private String name;
        private String slug;
        private String color;
        private String icon;
        private Boolean isSystem;
        private List<String> keywords;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryRequest {
        private String name;
        private String color;
        private String icon;
        private List<String> keywords;
    }

    // ── Parse / Save flow ────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedTransactionDto {
        private String description;
        private String merchantName;
        private BigDecimal amount;
        private LocalDate transactionDate;
        private Boolean isDebit;
        private Long suggestedCategoryId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParseResponse {
        private String fileName;
        private StatementType statementType;
        private String monthYear;
        private List<ParsedTransactionDto> parsed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveTransactionItem {
        private String description;
        private String merchantName;
        private BigDecimal amount;
        private LocalDate transactionDate;
        private Boolean isDebit;
        private Long categoryId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveStatementRequest {
        private String fileName;
        private StatementType statementType;
        private String monthYear;
        private List<SaveTransactionItem> transactions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateCategoryRequest {
        private Long categoryId;
    }
}

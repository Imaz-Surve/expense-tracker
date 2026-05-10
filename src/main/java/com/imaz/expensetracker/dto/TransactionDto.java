package com.imaz.expensetracker.dto;

import com.imaz.expensetracker.entity.Transaction.Category;
import com.imaz.expensetracker.entity.Transaction.StatementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
        private Category category;
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
        private Category category;
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
}
package com.imaz.expensetracker.service;

import com.imaz.expensetracker.dto.TransactionDto.*;
import com.imaz.expensetracker.entity.Statement;
import com.imaz.expensetracker.entity.Transaction;
import com.imaz.expensetracker.entity.Transaction.Category;
import com.imaz.expensetracker.entity.User;
import com.imaz.expensetracker.parser.StatementParserService;
import com.imaz.expensetracker.repository.StatementRepository;
import com.imaz.expensetracker.repository.TransactionRepository;
import com.imaz.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepo;
    private final StatementRepository statementRepo;
    private final UserRepository userRepo;
    private final StatementParserService parserService;

    @Transactional
    public UploadResponse uploadStatement(MultipartFile file,
                                          Transaction.StatementType statementType,
                                          String monthYear) throws Exception {
        User user = currentUser();

        // Parse PDF
        List<Transaction> parsed = parserService.parsePdf(file, statementType);

        // Save statement record
        Statement statement = Statement.builder()
                .fileName(file.getOriginalFilename())
                .statementType(statementType)
                .monthYear(monthYear)
                .transactionCount(parsed.size())
                .user(user)
                .build();
        statementRepo.save(statement);

        // Attach user and statement to each transaction, then save
        parsed.forEach(tx -> {
            tx.setUser(user);
            tx.setStatement(statement);
        });
        transactionRepo.saveAll(parsed);

        return UploadResponse.builder()
                .statementId(statement.getId())
                .fileName(file.getOriginalFilename())
                .parsedCount(parsed.size())
                .message("Successfully parsed " + parsed.size() + " transactions")
                .build();
    }

    // â”€â”€ Dashboard â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public DashboardResponse getDashboard() {
        User user = currentUser();
        Long userId = user.getId();

        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);

        // This month spending
        List<Transaction> monthTxs = transactionRepo
                .findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
                        userId, startOfMonth, now);
        BigDecimal thisMonth = monthTxs.stream()
                .filter(Transaction::getIsDebit)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // All-time total
        List<Transaction> allTxs = transactionRepo
                .findByUserIdOrderByTransactionDateDesc(userId);
        BigDecimal allTime = allTxs.stream()
                .filter(Transaction::getIsDebit)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Category breakdown (this month)
        List<Object[]> rawCategories = transactionRepo.sumByCategory(userId, startOfMonth, now);
        List<CategorySummary> categories = buildCategorySummaries(rawCategories, thisMonth);

        // Monthly trend
        List<Object[]> rawMonthly = transactionRepo.monthlySpending(userId);
        List<MonthlySummary> monthly = rawMonthly.stream()
                .map(row -> MonthlySummary.builder()
                        .month((String) row[0])
                        .total(BigDecimal.valueOf(((Number) row[1]).doubleValue()))
                        .build())
                .collect(Collectors.toList());

        // Recent 10 transactions
        List<TransactionResponse> recent = allTxs.stream()
                .limit(10)
                .map(this::toResponse)
                .collect(Collectors.toList());

        return DashboardResponse.builder()
                .totalSpentThisMonth(thisMonth)
                .totalSpentAllTime(allTime)
                .categoryBreakdown(categories)
                .monthlyTrend(monthly)
                .recentTransactions(recent)
                .totalTransactions((long) allTxs.size())
                .build();
    }

    // â”€â”€ Transactions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public List<TransactionResponse> getTransactions(String from, String to, Category category) {
        User user = currentUser();
        List<Transaction> txs;

        if (category != null) {
            txs = transactionRepo.findByUserIdAndCategoryOrderByTransactionDateDesc(
                    user.getId(), category);
        } else if (from != null && to != null) {
            LocalDate fromDate = LocalDate.parse(from);
            LocalDate toDate = LocalDate.parse(to);
            txs = transactionRepo
                    .findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
                            user.getId(), fromDate, toDate);
        } else {
            txs = transactionRepo.findByUserIdOrderByTransactionDateDesc(user.getId());
        }

        return txs.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<StatementResponse> getStatements() {
        User user = currentUser();
        return statementRepo.findByUserIdOrderByUploadedAtDesc(user.getId())
                .stream().map(this::toStatementResponse).collect(Collectors.toList());
    }

    @Transactional
    public void deleteStatement(Long statementId) {
        User user = currentUser();
        Statement statement = statementRepo.findById(statementId)
                .orElseThrow(() -> new IllegalArgumentException("Statement not found"));
        if (!statement.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Access denied");
        }
        statementRepo.delete(statement);
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private List<CategorySummary> buildCategorySummaries(List<Object[]> raw, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) return List.of();
        return raw.stream().map(row -> {
            Category cat = Category.valueOf((String) row[0]);
            BigDecimal catTotal = BigDecimal.valueOf(((Number) row[1]).doubleValue());
            double pct = catTotal.divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
            return CategorySummary.builder()
                    .category(cat)
                    .total(catTotal)
                    .percentage(pct)
                    .build();
        }).collect(Collectors.toList());
    }

    private TransactionResponse toResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .description(tx.getDescription())
                .merchantName(tx.getMerchantName())
                .amount(tx.getAmount())
                .transactionDate(tx.getTransactionDate())
                .category(tx.getCategory())
                .statementType(tx.getStatementType())
                .isDebit(tx.getIsDebit())
                .createdAt(tx.getCreatedAt())
                .statementId(tx.getStatement() != null ? tx.getStatement().getId() : null)
                .build();
    }

    private StatementResponse toStatementResponse(Statement s) {
        return StatementResponse.builder()
                .id(s.getId())
                .fileName(s.getFileName())
                .statementType(s.getStatementType())
                .monthYear(s.getMonthYear())
                .transactionCount(s.getTransactionCount())
                .uploadedAt(s.getUploadedAt())
                .build();
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
package com.imaz.expensetracker.service;

import com.imaz.expensetracker.dto.TransactionDto.*;
import com.imaz.expensetracker.entity.Category;
import com.imaz.expensetracker.entity.Statement;
import com.imaz.expensetracker.entity.Transaction;
import com.imaz.expensetracker.entity.User;
import com.imaz.expensetracker.parser.StatementParserService;
import com.imaz.expensetracker.repository.CategoryRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepo;
    private final StatementRepository statementRepo;
    private final UserRepository userRepo;
    private final CategoryRepository categoryRepo;
    private final CategoryService categoryService;
    private final StatementParserService parserService;

    // ── Parse (no persist) ────────────────────────────────────────────────────
    public ParseResponse parseStatement(MultipartFile file,
                                        Transaction.StatementType statementType,
                                        String monthYear) throws Exception {
        User user = currentUser();
        List<ParsedTransactionDto> parsed = parserService.parsePdf(file, statementType, user.getId());
        return ParseResponse.builder()
                .fileName(file.getOriginalFilename())
                .statementType(statementType)
                .monthYear(monthYear)
                .parsed(parsed)
                .build();
    }

    // ── Save reviewed transactions ────────────────────────────────────────────
    @Transactional
    public UploadResponse saveStatement(SaveStatementRequest req) {
        User user = currentUser();
        if (req.getTransactions() == null || req.getTransactions().isEmpty()) {
            throw new IllegalArgumentException("No transactions to save");
        }

        Statement statement = Statement.builder()
                .fileName(req.getFileName())
                .statementType(req.getStatementType())
                .monthYear(req.getMonthYear())
                .transactionCount(req.getTransactions().size())
                .user(user)
                .build();
        statementRepo.save(statement);

        // Preload categories used in this batch
        Map<Long, Category> catCache = new HashMap<>();
        for (SaveTransactionItem it : req.getTransactions()) {
            if (it.getCategoryId() != null && !catCache.containsKey(it.getCategoryId())) {
                Category c = categoryRepo.findByIdAndUserId(it.getCategoryId(), user.getId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Category " + it.getCategoryId() + " not found"));
                catCache.put(c.getId(), c);
            }
        }

        List<Transaction> txs = new ArrayList<>(req.getTransactions().size());
        for (SaveTransactionItem it : req.getTransactions()) {
            txs.add(Transaction.builder()
                    .description(it.getDescription())
                    .merchantName(it.getMerchantName())
                    .amount(it.getAmount())
                    .transactionDate(it.getTransactionDate())
                    .isDebit(it.getIsDebit() == null ? true : it.getIsDebit())
                    .category(it.getCategoryId() == null ? null : catCache.get(it.getCategoryId()))
                    .statementType(req.getStatementType())
                    .user(user)
                    .statement(statement)
                    .build());
        }
        transactionRepo.saveAll(txs);

        return UploadResponse.builder()
                .statementId(statement.getId())
                .fileName(req.getFileName())
                .parsedCount(txs.size())
                .message("Saved " + txs.size() + " transactions")
                .build();
    }

    // ── Inline category edit ──────────────────────────────────────────────────
    @Transactional
    public TransactionResponse updateCategory(Long transactionId, Long categoryId) {
        User user = currentUser();
        Transaction tx = transactionRepo.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if (!tx.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Access denied");
        }
        if (categoryId == null) {
            tx.setCategory(null);
        } else {
            Category c = categoryRepo.findByIdAndUserId(categoryId, user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            tx.setCategory(c);
        }
        return toResponse(transactionRepo.save(tx));
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────
    public DashboardResponse getDashboard() {
        User user = currentUser();
        Long userId = user.getId();

        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);

        List<Transaction> monthTxs = transactionRepo
                .findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(userId, startOfMonth, now);
        BigDecimal thisMonth = monthTxs.stream()
                .filter(Transaction::getIsDebit)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Transaction> allTxs = transactionRepo.findByUserIdOrderByTransactionDateDesc(userId);
        BigDecimal allTime = allTxs.stream()
                .filter(Transaction::getIsDebit)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Object[]> rawCategories = transactionRepo.sumByCategory(userId, startOfMonth, now);
        List<CategorySummary> categories = buildCategorySummaries(rawCategories, thisMonth, userId);

        List<Object[]> rawMonthly = transactionRepo.monthlySpending(userId);
        List<MonthlySummary> monthly = rawMonthly.stream()
                .map(row -> MonthlySummary.builder()
                        .month((String) row[0])
                        .total(BigDecimal.valueOf(((Number) row[1]).doubleValue()))
                        .build())
                .collect(Collectors.toList());

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

    // ── Transactions listing ──────────────────────────────────────────────────
    public List<TransactionResponse> getTransactions(String from, String to, Long categoryId) {
        User user = currentUser();
        List<Transaction> txs;

        if (categoryId != null) {
            txs = transactionRepo.findByUserIdAndCategoryIdOrderByTransactionDateDesc(user.getId(), categoryId);
        } else if (from != null && to != null) {
            LocalDate fromDate = LocalDate.parse(from);
            LocalDate toDate = LocalDate.parse(to);
            txs = transactionRepo
                    .findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(user.getId(), fromDate, toDate);
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
        // Explicitly delete child transactions first (Statement's OneToMany may be lazy-empty)
        List<Transaction> txs = transactionRepo.findByStatementId(statementId);
        transactionRepo.deleteAll(txs);
        statementRepo.delete(statement);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<CategorySummary> buildCategorySummaries(List<Object[]> raw, BigDecimal total, Long userId) {
        if (total.compareTo(BigDecimal.ZERO) == 0 || raw.isEmpty()) return List.of();
        Map<Long, Category> byId = categoryRepo.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
        List<CategorySummary> out = new ArrayList<>(raw.size());
        for (Object[] row : raw) {
            Long catId = ((Number) row[0]).longValue();
            BigDecimal catTotal = BigDecimal.valueOf(((Number) row[1]).doubleValue());
            Long count = ((Number) row[2]).longValue();
            double pct = catTotal.divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
            Category c = byId.get(catId);
            if (c == null) continue;
            out.add(CategorySummary.builder()
                    .category(categoryService.toDto(c))
                    .total(catTotal)
                    .count(count)
                    .percentage(pct)
                    .build());
        }
        return out;
    }

    private TransactionResponse toResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .description(tx.getDescription())
                .merchantName(tx.getMerchantName())
                .amount(tx.getAmount())
                .transactionDate(tx.getTransactionDate())
                .category(tx.getCategory() == null ? null : categoryService.toDto(tx.getCategory()))
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
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}

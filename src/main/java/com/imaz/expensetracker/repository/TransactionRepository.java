package com.imaz.expensetracker.repository;

import com.imaz.expensetracker.entity.Transaction;
import com.imaz.expensetracker.entity.Transaction.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderByTransactionDateDesc(Long userId);

    List<Transaction> findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            Long userId, LocalDate from, LocalDate to);

    List<Transaction> findByUserIdAndCategoryOrderByTransactionDateDesc(
            Long userId, Category category);

    @Query("""
        SELECT t.category AS category, SUM(t.amount) AS total
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.isDebit = true
          AND t.transactionDate BETWEEN :from AND :to
        GROUP BY t.category
        ORDER BY total DESC
        """)
    List<Object[]> sumByCategory(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
        SELECT FUNCTION('DATE_FORMAT', t.transactionDate, '%Y-%m') AS month,
               SUM(t.amount) AS total
        FROM Transaction t
        WHERE t.user.id = :userId AND t.isDebit = true
        GROUP BY month
        ORDER BY month ASC
        """)
    List<Object[]> monthlySpending(@Param("userId") Long userId);

    List<Transaction> findByStatementId(Long statementId);
}

package com.imaz.expensetracker.repository;

import com.imaz.expensetracker.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserIdOrderByTransactionDateDesc(Long userId);

    List<Transaction> findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            Long userId, LocalDate from, LocalDate to);

    List<Transaction> findByUserIdAndCategoryIdOrderByTransactionDateDesc(
            Long userId, Long categoryId);

    @Query("""
        SELECT t.category.id AS categoryId, SUM(t.amount) AS total, COUNT(t) AS cnt
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.isDebit = true
          AND t.transactionDate BETWEEN :from AND :to
          AND t.category IS NOT NULL
        GROUP BY t.category.id
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
        GROUP BY FUNCTION('DATE_FORMAT', t.transactionDate, '%Y-%m')
        ORDER BY month ASC
        """)
    List<Object[]> monthlySpending(@Param("userId") Long userId);

    List<Transaction> findByStatementId(Long statementId);
}

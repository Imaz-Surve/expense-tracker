package com.imaz.expensetracker.repository;

import com.imaz.expensetracker.entity.Statement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StatementRepository extends JpaRepository<Statement, Long> {
    List<Statement> findByUserIdOrderByUploadedAtDesc(Long userId);
}

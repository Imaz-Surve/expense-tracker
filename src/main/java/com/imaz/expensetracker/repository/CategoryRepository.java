package com.imaz.expensetracker.repository;

import com.imaz.expensetracker.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByUserIdOrderByNameAsc(Long userId);

    Optional<Category> findByIdAndUserId(Long id, Long userId);

    Optional<Category> findByUserIdAndSlug(Long userId, String slug);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
}

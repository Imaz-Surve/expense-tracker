package com.imaz.expensetracker.controller;

import com.imaz.expensetracker.dto.TransactionDto.CategoryDto;
import com.imaz.expensetracker.dto.TransactionDto.CategoryRequest;
import com.imaz.expensetracker.entity.User;
import com.imaz.expensetracker.repository.UserRepository;
import com.imaz.expensetracker.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final UserRepository userRepo;

    @GetMapping
    public ResponseEntity<List<CategoryDto>> list() {
        return ResponseEntity.ok(categoryService.listForUser(currentUser().getId()));
    }

    @PostMapping
    public ResponseEntity<CategoryDto> create(@RequestBody CategoryRequest req) {
        return ResponseEntity.ok(categoryService.create(currentUser(), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDto> update(@PathVariable Long id, @RequestBody CategoryRequest req) {
        return ResponseEntity.ok(categoryService.update(currentUser(), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(currentUser(), id);
        return ResponseEntity.noContent().build();
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}

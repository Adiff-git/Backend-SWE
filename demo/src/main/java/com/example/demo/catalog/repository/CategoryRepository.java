package com.example.demo.catalog.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.demo.catalog.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
}

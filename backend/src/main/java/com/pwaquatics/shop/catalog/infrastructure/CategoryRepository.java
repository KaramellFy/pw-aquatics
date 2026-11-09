package com.pwaquatics.shop.catalog.infrastructure;

import com.pwaquatics.shop.catalog.domain.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.active = true ORDER BY c.sortOrder, c.name")
    List<Category> findAllActive();
}

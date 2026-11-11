package com.pwaquatics.shop.catalog.domain;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A node in the category tree, e.g. Shrimp → Neocaridina. */
@Entity
@Table(name = "categories")
public class Category extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected Category() {}

    public Category(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    /**
     * Changes the category. A category cannot become its own parent or a child of one of
     * its descendants.
     */
    public void update(String name, String slug, Category parent, String description, int sortOrder, boolean active) {
        for (Category ancestor = parent; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.equals(this)) {
                throw new BusinessRuleException("CATEGORY_CYCLE", "A category cannot be placed inside itself.");
            }
        }
        this.name = name;
        this.slug = slug;
        this.parent = parent;
        this.description = description;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public Category getParent() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}

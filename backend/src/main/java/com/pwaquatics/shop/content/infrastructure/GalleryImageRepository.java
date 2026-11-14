package com.pwaquatics.shop.content.infrastructure;

import com.pwaquatics.shop.content.domain.GalleryImage;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface GalleryImageRepository extends JpaRepository<GalleryImage, UUID> {

    /** Published photos in gallery order. */
    Page<GalleryImage> findByPublishedTrueOrderBySortOrderAscCreatedAtAsc(Pageable pageable);

    /** Every photo in gallery order (admin). */
    List<GalleryImage> findAllByOrderBySortOrderAscCreatedAtAsc();

    /** Every photo, locked, so two reorders cannot interleave. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GalleryImage g ORDER BY g.sortOrder, g.createdAt")
    List<GalleryImage> lockAll();
}

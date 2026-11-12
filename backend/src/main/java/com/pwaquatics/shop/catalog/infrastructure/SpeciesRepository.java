package com.pwaquatics.shop.catalog.infrastructure;

import com.pwaquatics.shop.catalog.domain.Species;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpeciesRepository extends JpaRepository<Species, UUID> {

    List<Species> findByTradeRestrictedFalseOrderByScientificNameAsc();

    Optional<Species> findBySlugAndTradeRestrictedFalse(String slug);

    List<Species> findAllByOrderByScientificNameAsc();

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    boolean existsByScientificNameIgnoreCase(String scientificName);

    boolean existsByScientificNameIgnoreCaseAndIdNot(String scientificName, UUID id);
}

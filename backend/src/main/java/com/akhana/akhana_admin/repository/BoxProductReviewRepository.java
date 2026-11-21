package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.BoxProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;
import java.util.UUID;

public interface BoxProductReviewRepository extends JpaRepository<BoxProductReview, UUID> {

    boolean existsByBoxProductId(UUID boxProductId);

    @Query("SELECT r.boxProductId FROM BoxProductReview r")
    Set<UUID> findReviewedBoxProductIds();
}

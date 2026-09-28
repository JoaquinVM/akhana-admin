package com.akhana.akhana_admin.repository;

import com.akhana.akhana_admin.model.Tag;
import com.akhana.akhana_admin.model.TagStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByStatusNotOrderByNameAsc(TagStatus status);

    List<Tag> findByStatusOrderByNameAsc(TagStatus status);

    boolean existsByNameIgnoreCaseAndStatusNot(String name, TagStatus status);

    boolean existsByNameIgnoreCaseAndStatusNotAndIdNot(String name, TagStatus status, UUID id);

    @Query("SELECT t FROM Tag t WHERE t.status != :excludedStatus AND " +
           "LOWER(t.name) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "ORDER BY t.name ASC")
    List<Tag> searchTags(@Param("term") String term, @Param("excludedStatus") TagStatus excludedStatus);

    @Query("SELECT t FROM Tag t WHERE t.status = :status AND " +
           "LOWER(t.name) LIKE LOWER(CONCAT('%', :term, '%')) " +
           "ORDER BY t.name ASC")
    List<Tag> searchTagsByStatus(@Param("term") String term, @Param("status") TagStatus status);
}

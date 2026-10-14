package com.console.demo.repositories;

import com.console.demo.entities.SummaryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SummaryRepository extends JpaRepository<SummaryEntity, Long> {

    Optional<SummaryEntity> findTopByOrderByCreatedAtDesc();

    List<SummaryEntity> findTop3ByOrderByCreatedAtDesc();

    @Query("""
    select s from SummaryEntity s
    where s.vector is not null
    order by cosine_distance(s.vector, :query)
    """)
    List<SummaryEntity> findSimilar(@Param("query") float[] query, Pageable pageable);

    List<SummaryEntity> findByVectorIsNull(Pageable pageable);
}
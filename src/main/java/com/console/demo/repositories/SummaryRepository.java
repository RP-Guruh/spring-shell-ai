package com.console.demo.repositories;

import com.console.demo.entities.SummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SummaryRepository extends JpaRepository<SummaryEntity, Long> {

    Optional<SummaryEntity> findTopByOrderByCreatedAtDesc();

    List<SummaryEntity> findTop3ByOrderByCreatedAtDesc();
}

package com.console.demo.repositories;

import com.console.demo.dto.MessageDto;
import com.console.demo.entities.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    List<MessageDto> findAllByOrderByIdAsc();
    List<MessageDto> findTop5ByOrderByIdDesc();

    List<MessageDto> findBySummarizedFalseOrderByIdAsc();

    long countBySummarizedFalse();

    @EntityGraph(attributePaths = "conversation")
    List<MessageEntity> findTop10BySummarizedFalseOrderByIdAsc();

    @Modifying
    @Query("update MessageEntity m set m.summarized = true where m.id in :ids")
    void markSummarized(@Param("ids") List<Long> ids);
}

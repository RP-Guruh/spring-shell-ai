package com.console.demo.repositories;

import com.console.demo.dto.MessageDto;
import com.console.demo.entities.MessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    List<MessageDto> findAllByOrderByIdAsc();
    List<MessageDto> findTop5ByOrderByIdDesc();

    List<MessageEntity> findBySummarizedFalseOrderByIdAsc();

    long countBySummarizedFalse();

    @EntityGraph(attributePaths = "conversation")
    List<MessageEntity> findTop10BySummarizedFalseOrderByIdAsc();

    @Modifying
    @Query("update MessageEntity m set m.summarized = true where m.id in :ids")
    void markSummarized(@Param("ids") List<Long> ids);

    // [vector] Cari pasangan tanya-jawab yang mirip.
    // Hasil per baris: [MessageEntity jawaban, jarak cosine]. Jarak makin kecil makin mirip.
    // onlySummarized = true -> hanya pesan yang sudah dirangkum (tidak ada di history).
    @Query("""
        select m, cosine_distance(m.vector, :query)
        from MessageEntity m
        where m.vector is not null
          and m.role = 'assistant'
          and cosine_distance(m.vector, :query) <= :maxDistance
          and (:onlySummarized = false or m.summarized = true)
        order by cosine_distance(m.vector, :query)
        """)
    List<Object[]> findSimilar(@Param("query") float[] query,
                               @Param("maxDistance") double maxDistance,
                               @Param("onlySummarized") boolean onlySummarized,
                               Pageable pageable);

    // [vector] Pertanyaan dari sebuah jawaban = baris "user" terdekat sebelumnya.
    // Aman karena MessageService.addPair() menyimpan user lalu assistant berurutan.
    Optional<MessageEntity> findFirstByIdLessThanAndRoleOrderByIdDesc(Long id, String role);

    // [vector] Untuk backfill. Pakai kursor id supaya baris yang gagal di-embed tidak diulang terus.
    List<MessageEntity> findByRoleAndVectorIsNullAndIdGreaterThanOrderByIdAsc(
            String role, Long id, Pageable pageable);
}
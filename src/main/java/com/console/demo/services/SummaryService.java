package com.console.demo.services;

import com.console.demo.entities.MessageEntity;
import com.console.demo.entities.SummaryEntity;
import com.console.demo.repositories.ConversationRepository;
import com.console.demo.repositories.MessageRepository;
import com.console.demo.repositories.SummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SummaryService {
    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private final SummaryRepository summaryRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final EmbeddingModel embeddingModel;

    public SummaryService(SummaryRepository summaryRepository,
                          ConversationRepository conversationRepository,
                          MessageRepository messageRepository,
                          EmbeddingModel embeddingModel) {
        this.summaryRepository = summaryRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.embeddingModel = embeddingModel;
    }

    public SummaryEntity add(Long conversationId, Long lastMessageId, String content, Integer countMessage) {
        SummaryEntity c = new SummaryEntity();
        if (conversationId != null) {
            c.setConversation(conversationRepository.getReferenceById(conversationId));
        }
        c.setLastMessageId(lastMessageId);
        c.setContent(content);
        c.setCountMessage(countMessage);
        c.setVector(embedSafely(content));
        return summaryRepository.save(c);
    }

    public Optional<SummaryEntity> getLatest() {
        return summaryRepository.findTopByOrderByCreatedAtDesc();
    }

    public List<SummaryEntity> getLatest3() {
        return summaryRepository.findTop3ByOrderByCreatedAtDesc();
    }

    @Transactional
    public void saveAndMark(List<MessageEntity> batch, String content) {
        MessageEntity last = batch.get(batch.size() - 1);
        Long conversationId = last.getConversation().getId();

        add(conversationId, last.getId(), content, batch.size());
        messageRepository.markSummarized(batch.stream().map(MessageEntity::getId).toList());
    }

    /** Cari summary yang paling mirip dengan teks pencarian. */
    @Transactional(readOnly = true)
    public List<SummaryEntity> searchSimilar(String query, int limit) {
        float[] queryVector = embeddingModel.embed(query);
        return summaryRepository.findSimilar(queryVector, PageRequest.of(0, limit));
    }

    /** Isi vector untuk summary lama atau yang gagal di-embed. Return: jumlah yang berhasil. */
    @Transactional
    public int backfillVectors(int batchSize) {
        int done = 0;
        for (SummaryEntity s : summaryRepository.findByVectorIsNull(PageRequest.of(0, batchSize))) {
            float[] v = embedSafely(s.getContent());
            if (v != null) {
                s.setVector(v);
                done++;
            }
        }
        return done;
    }

    private float[] embedSafely(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.warn("Embedding gagal, vector dikosongkan dan bisa di-backfill nanti", e);
            return null;
        }
    }


}

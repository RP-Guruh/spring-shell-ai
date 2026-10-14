package com.console.demo.services;

import com.console.demo.dto.MessageDto;
import com.console.demo.dto.PastExchange;
import com.console.demo.entities.MessageEntity;
import com.console.demo.repositories.ConversationRepository;
import com.console.demo.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    // Teks yang di-embed dipotong segini supaya embedding di CPU tidak lambat.
    // Topik biasanya sudah terlihat di bagian awal pertanyaan + jawaban.
    private static final int EMBED_MAX_CHARS = 2000;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final EmbeddingModel embeddingModel;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          EmbeddingModel embeddingModel) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.embeddingModel = embeddingModel;
    }

    public MessageEntity add(Long conversationId, String role, String content, Integer totalToken) {
        return add(conversationId, role, content, false, totalToken);
    }

    public MessageEntity add(Long conversationId, String role, String content, boolean summarized, Integer totalToken) {
        return save(conversationId, role, content, summarized, totalToken, null);
    }

    /**
     * Simpan satu pasangan tanya-jawab. Vector dibuat dari gabungan keduanya
     * dan disimpan di baris assistant. Kalau Ollama gagal, vector dibiarkan null
     * dan bisa diisi nanti lewat backfill-vector.
     */
    public void addPair(Long conversationId, String question, String answer,
                        Integer questionTokens, Integer answerTokens) {
        float[] vector = embedSafely(pairText(question, answer));   // sebelum menyentuh database

        save(conversationId, "user", question, false, questionTokens, null);
        save(conversationId, "assistant", answer, false, answerTokens, vector);
    }

    public List<MessageDto> getRecent() {
        return messageRepository.findBySummarizedFalseOrderByIdAsc().stream()
                .map(m -> new MessageDto(m.getRole(), m.getContent()))
                .toList();
    }

    /**
     * Cari pasangan tanya-jawab lama yang mirip dengan teks.
     *
     * @param maxDistance   batas jarak cosine (0 = identik). Pakai 2.0 untuk tanpa batas.
     * @param onlySummarized true = hanya yang sudah dirangkum (untuk RAG di chat)
     */
    @Transactional(readOnly = true)
    public List<PastExchange> searchPast(String query, int limit, double maxDistance, boolean onlySummarized) {
        float[] queryVector = embeddingModel.embed(query);
        List<Object[]> rows = messageRepository.findSimilar(
                queryVector, maxDistance, onlySummarized, PageRequest.of(0, limit));

        List<PastExchange> result = new ArrayList<>();
        for (Object[] row : rows) {
            MessageEntity answer = (MessageEntity) row[0];
            double distance = ((Number) row[1]).doubleValue();
            result.add(new PastExchange(answer.getId(), findQuestion(answer), answer.getContent(), distance));
        }
        return result;
    }

    /**
     * Isi vector untuk pasangan lama. Return: jumlah yang berhasil.
     * onProgress dipanggil setelah tiap batch (untuk menampilkan progres).
     */
    public int backfillVectors(IntConsumer onProgress) {
        long afterId = 0;
        int done = 0;

        while (true) {
            List<MessageEntity> batch = messageRepository
                    .findByRoleAndVectorIsNullAndIdGreaterThanOrderByIdAsc(
                            "assistant", afterId, PageRequest.of(0, 20));
            if (batch.isEmpty()) break;

            for (MessageEntity answer : batch) {
                afterId = answer.getId();   // kursor maju walau embedding gagal
                float[] vector = embedSafely(pairText(findQuestion(answer), answer.getContent()));
                if (vector == null) continue;
                answer.setVector(vector);
                messageRepository.save(answer);
                done++;
            }
            onProgress.accept(done);
        }
        return done;
    }

    // ---------------- helper ----------------

    private MessageEntity save(Long conversationId, String role, String content,
                               boolean summarized, Integer totalToken, float[] vector) {
        MessageEntity c = new MessageEntity();
        c.setConversation(conversationRepository.getReferenceById(conversationId));
        c.setRole(role);
        c.setContent(content);
        c.setSummarized(summarized);
        c.setTotalToken(totalToken);
        c.setVector(vector);
        return messageRepository.save(c);
    }

    private String findQuestion(MessageEntity answer) {
        return messageRepository.findFirstByIdLessThanAndRoleOrderByIdDesc(answer.getId(), "user")
                .map(MessageEntity::getContent)
                .orElse("");
    }

    private static String pairText(String question, String answer) {
        String text = "Tanya: " + question + "\nJawab: " + answer;
        return text.length() > EMBED_MAX_CHARS ? text.substring(0, EMBED_MAX_CHARS) : text;
    }

    private float[] embedSafely(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.warn("Embedding gagal, vector dikosongkan (bisa di-backfill nanti)", e);
            return null;
        }
    }
}
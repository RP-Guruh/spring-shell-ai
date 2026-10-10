package com.console.demo.services;

import com.console.demo.dto.ChatMessage;
import com.console.demo.entities.MessageEntity;
import com.console.demo.entities.SummaryEntity;
import com.console.demo.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SummarizerService {
    private static final Logger log = LoggerFactory.getLogger(SummarizerService.class);
    private static final int BATCH_SIZE = 10;

    private final MessageRepository messageRepository;
    private final SummaryService summaryService;
    private final SummaryGenerate summaryGenerate;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SummarizerService(MessageRepository messageRepository,
                             SummaryService summaryService,
                             SummaryGenerate summaryGenerate) {
        this.messageRepository = messageRepository;
        this.summaryService = summaryService;
        this.summaryGenerate = summaryGenerate;
    }

    @Async
    public void summarizeIfNeeded() {
        if (!running.compareAndSet(false, true)) return;
        try {
            if (messageRepository.countBySummarizedFalse() < BATCH_SIZE) return;
            log.info("[{}] Mulai peringkasan", Thread.currentThread().getName());

            List<MessageEntity> batch = messageRepository.findTop10BySummarizedFalseOrderByIdAsc();
            if (batch.size() < BATCH_SIZE) return;

            String oldSummary = summaryService.getLatest()
                    .map(SummaryEntity::getContent)
                    .orElse(null);

            List<ChatMessage> messages = batch.stream()
                    .map(m -> new ChatMessage(m.getRole(), m.getContent()))
                    .toList();

            Optional<String> result = summaryGenerate.generate(oldSummary, messages);
            if (result.isEmpty()) {
                log.debug("Ringkasan gagal/terpotong, dicoba lagi nanti");
                return;
            }

            summaryService.saveAndMark(batch, result.get());
            log.info("[{}] Selesai, {} pesan ditandai", Thread.currentThread().getName(), batch.size());   // penanda selesai

        } catch (Exception e) {
            log.debug("Peringkasan gagal", e);
        } finally {
            running.set(false);
        }
    }
}

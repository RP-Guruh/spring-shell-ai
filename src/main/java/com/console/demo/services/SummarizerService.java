package com.console.demo.services;

import com.console.demo.dto.ChatMessage;
import com.console.demo.entities.MessageEntity;
import com.console.demo.entities.SummaryEntity;
import com.console.demo.repositories.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SummarizerService {
    private static final Logger log = LoggerFactory.getLogger(SummarizerService.class);

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


    public boolean summarize(int keepLast) {
        if (!running.compareAndSet(false, true)) return false;
        try {
            List<MessageEntity> pending = messageRepository.findBySummarizedFalseOrderByIdAsc();

            int foldCount = pending.size() - Math.max(keepLast, 1);

            while (foldCount > 0 && !"user".equalsIgnoreCase(pending.get(foldCount).getRole())) {
                foldCount--;
            }
            if (foldCount <= 0) return false;

            List<MessageEntity> batch = new ArrayList<>(pending.subList(0, foldCount));
            log.info("[{}] Mulai peringkasan {} pesan", Thread.currentThread().getName(), batch.size());

            String oldSummary = summaryService.getLatest()
                    .map(SummaryEntity::getContent)
                    .orElse(null);

            List<ChatMessage> messages = batch.stream()
                    .map(m -> new ChatMessage(m.getRole(), m.getContent()))
                    .toList();

            Optional<String> result = summaryGenerate.generate(oldSummary, messages);
            if (result.isEmpty()) {
                log.warn("Ringkasan gagal/terpotong, dicoba lagi nanti");
                return false;
            }

            summaryService.saveAndMark(batch, result.get());
            log.info("[{}] Selesai, {} pesan ditandai", Thread.currentThread().getName(), batch.size());
            return true;

        } catch (Exception e) {
            log.warn("Peringkasan gagal", e);
            return false;
        } finally {
            running.set(false);
        }
    }
}
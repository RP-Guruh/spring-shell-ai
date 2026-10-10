package com.console.demo.services;

import com.console.demo.entities.MessageEntity;
import com.console.demo.entities.SummaryEntity;
import com.console.demo.repositories.ConversationRepository;
import com.console.demo.repositories.MessageRepository;
import com.console.demo.repositories.SummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SummaryService {
    private final SummaryRepository summaryRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public SummaryService(SummaryRepository summaryRepository,
                          ConversationRepository conversationRepository,
                          MessageRepository messageRepository) {
        this.summaryRepository = summaryRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public SummaryEntity add(Long conversationId, Long lastMessageId, String content, Integer countMessage) {
        SummaryEntity c = new SummaryEntity();
        if (conversationId != null) {
            c.setConversation(conversationRepository.getReferenceById(conversationId));
        }
        c.setLastMessageId(lastMessageId);
        c.setContent(content);
        c.setCountMessage(countMessage);
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
}

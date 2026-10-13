package com.console.demo.services;

import com.console.demo.dto.MessageDto;
import com.console.demo.entities.MessageEntity;
import com.console.demo.repositories.ConversationRepository;
import com.console.demo.repositories.MessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    public MessageService(MessageRepository messageRepository, ConversationRepository conversationRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
    }

    public MessageEntity add(Long conversationId, String role, String content, Integer totalToken) {
        return add(conversationId, role, content, false, totalToken);
    }

    public MessageEntity add(Long conversationId, String role, String content, boolean summarized, Integer totalToken) {
        MessageEntity c = new MessageEntity();
        c.setConversation(conversationRepository.getReferenceById(conversationId));
        c.setRole(role);
        c.setContent(content);
        c.setSummarized(summarized);
        c.setTotalToken(totalToken);

        return messageRepository.save(c);
    }

    public List<MessageDto> getRecent() {
        return messageRepository.findBySummarizedFalseOrderByIdAsc().stream()
                .map(m -> new MessageDto(m.getRole(), m.getContent()))
                .toList();
    }
}
package com.console.demo.services;

import com.console.demo.dto.MessageDto;
import com.console.demo.entities.MessageEntity;
import com.console.demo.repositories.ConversationRepository;
import com.console.demo.repositories.MessageRepository;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;


    public MessageService(MessageRepository messageRepository, ConversationRepository conversationRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
    }

    public MessageEntity add(Long conversationId, String role, String content) {
        return add(conversationId, role, content, false);
    }

    public MessageEntity add(Long conversationId, String role, String content, boolean summarized) {
        MessageEntity c = new MessageEntity();
        c.setConversation(conversationRepository.getReferenceById(conversationId));
        c.setRole(role);
        c.setContent(content);
        c.setSummarized(summarized);
        return messageRepository.save(c);
    }


    public List<MessageDto> getRecent() {
        List<MessageDto> recent = new ArrayList<>(messageRepository.findTop5ByOrderByIdDesc());
        Collections.reverse(recent);
        return recent;
    }
}

package com.console.demo.services;

import com.console.demo.entities.ConversationEntity;
import com.console.demo.repositories.ConversationRepository;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {
    private final ConversationRepository conversationRepository;

    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    public ConversationEntity add(String title){
        ConversationEntity c = new ConversationEntity();
        c.setTitle(title);
        return conversationRepository.save(c);
    }
}

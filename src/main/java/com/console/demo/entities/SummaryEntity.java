package com.console.demo.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "summaries")
public class SummaryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "count_message")
    private Integer countMessage;

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public ConversationEntity getConversation() { return conversation; }
    public void setConversation(ConversationEntity conversation) { this.conversation = conversation; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Long getLastMessageId() { return lastMessageId; }
    public void setLastMessageId(Long lastMessageId) { this.lastMessageId = lastMessageId; }

    public Integer getCountMessage() { return countMessage; }
    public void setCountMessage(Integer countMessage) { this.countMessage = countMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }

}

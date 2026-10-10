package com.console.demo.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;


@Entity
@Table(name="message")
public class MessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @Column(nullable = false, name = "role")
    private String role; // ai atau user

    @Column(nullable = false, name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, name = "is_summarized")
    private Boolean summarized;

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ConversationEntity getConversation() { return conversation; }
    public void setConversation(ConversationEntity conversation) { this.conversation = conversation; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Boolean getSummarized() { return summarized; }
    public void setSummarized(Boolean summarized) { this.summarized = summarized; }

    public LocalDateTime getCreatedAt() { return createdAt; }


}

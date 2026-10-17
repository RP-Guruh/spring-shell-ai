package com.console.demo.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(nullable = false, name = "total_token")
    private Integer totalToken;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 768)
    @Column(name = "vector")
    private float[] vector;

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

    public Integer getTotalToken() { return totalToken; }
    public void setTotalToken(Integer totalToken) { this.totalToken = totalToken; }

    public Boolean getSummarized() { return summarized; }
    public void setSummarized(Boolean summarized) { this.summarized = summarized; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public float[] getVector() { return vector; }
    public void setVector(float[] vector) { this.vector = vector; }


}

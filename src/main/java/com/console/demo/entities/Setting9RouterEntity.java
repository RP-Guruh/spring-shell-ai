package com.console.demo.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "setting_9routers")
public class Setting9RouterEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "api_key", nullable = false)
    private String apiKey;

    @Column(name = "combo_name", nullable = false)
    private String comboName;

    public Long getId() { return id; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getComboName() { return comboName; }
    public void setComboName(String comboName) { this.comboName = comboName; }

}

package com.console.demo.dto;

import jakarta.validation.constraints.NotBlank;

public class Setting9Router {
    @NotBlank(message = "Combo name wajib diisi")
    private String comboName;

    @NotBlank(message = "API key wajib diisi")
    private String apiKey;

    @NotBlank(message = "API URL wajib diisi")
    private String apiUrl;

    public String getComboName() { return comboName; }
    public void setComboName(String comboName) { this.comboName = comboName; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
}

package com.console.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelListResponse(List<ModelInfo> data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelInfo(
            String id,
            @JsonProperty("owned_by") String ownedBy,
            @JsonProperty("context_length") Long contextLength,
            @JsonProperty("max_completion_tokens") Long maxCompletionTokens,
            Capabilities capabilities){

    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Capabilities(boolean vision, boolean tools, boolean reasoning) {
    }
}

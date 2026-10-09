package com.console.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatChunk(List<Choice> choices) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Delta delta) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Delta(String content) {}
}
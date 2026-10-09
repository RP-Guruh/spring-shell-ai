package com.console.demo.dto;

import java.util.List;

public record ChatRequest(String model, List<ChatMessage> messages, boolean stream) {}


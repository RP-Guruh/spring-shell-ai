package com.console.demo.services;

import com.console.demo.dto.ChatChunk;
import com.console.demo.dto.ChatMessage;
import com.console.demo.dto.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Component
public class SummaryGenerate {

    private static final Logger log = LoggerFactory.getLogger(SummaryGenerate.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final int MAX_CHARS_PER_MESSAGE = 1500;

    private static final String SYSTEM_PROMPT = """
            Kamu adalah modul peringkas memori percakapan. Perbarui ringkasan
            dengan menggabungkan RINGKASAN LAMA dan PESAN BARU.

            Aturan:
            - Simpan hanya yang berguna untuk percakapan berikutnya: fakta tentang user,
              preferensi, keputusan, tugas yang berjalan, nama/angka/istilah penting,
              dan pertanyaan yang belum terjawab.
            - Buang basa-basi, sapaan, penjelasan umum, dan hal yang sudah selesai.
            - Jangan menyalin kalimat asli. Tulis padat dalam poin pendek.
            - Jika info baru bertentangan dengan info lama, pakai yang baru.
            - Untuk kode, catat tujuan dan nama file/fungsi saja, jangan salin kodenya.
            - Maksimal 250 kata. Jika melebihi, hapus info yang paling tidak penting.
            - Tulis dalam bahasa yang sama dengan percakapan.
            - Keluarkan HANYA isi ringkasan, tanpa pembuka atau penutup.
            """;

    private final RestClient restClient;
    private final String model;

    public SummaryGenerate(RestClient restClient,
                           @Value("${spring.ai.openai.chat.model}") String model) {
        this.restClient = restClient;
        this.model = model;
    }

    public Optional<String> generate(String oldSummary, List<ChatMessage> messages) {
        StringBuilder user = new StringBuilder();
        user.append("RINGKASAN LAMA:\n")
            .append(oldSummary == null || oldSummary.isBlank() ? "(kosong)" : oldSummary)
            .append("\n\nPESAN BARU:\n");

        for (ChatMessage m : messages) {
            String c = m.content();
            if (c.length() > MAX_CHARS_PER_MESSAGE) {
                c = c.substring(0, MAX_CHARS_PER_MESSAGE) + "...";
            }
            user.append(m.role()).append(": ").append(c).append("\n");
        }
        user.append("\nRINGKASAN BARU:");

        List<ChatMessage> request = List.of(
                new ChatMessage("system", SYSTEM_PROMPT),
                new ChatMessage("user", user.toString()));

        try {
            return restClient.post()
                    .uri("/v1/chat/completions")
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(new ChatRequest(model, request, true))
                    .exchange((req, response) -> {
                        if (response.getStatusCode().isError()) {
                            log.warn("Router membalas error {}", response.getStatusCode().value());
                            return Optional.<String>empty();
                        }

                        StringBuilder sb = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) continue;

                                String json = line.substring(5).trim();
                                if (json.isEmpty()) continue;
                                if (json.equals("[DONE]")) break;

                                ChatChunk chunk = MAPPER.readValue(json, ChatChunk.class);
                                if (chunk.choices() == null || chunk.choices().isEmpty()) continue;

                                ChatChunk.Delta delta = chunk.choices().get(0).delta();
                                if (delta == null || delta.content() == null) continue;

                                sb.append(delta.content());
                            }
                        }

                        String text = sb.toString().trim();
                        log.info("Ringkasan dibuat, panjang={}", text.length());
                        return text.isEmpty() ? Optional.<String>empty() : Optional.of(text);
                    });
        } catch (Exception e) {
            log.warn("Peringkasan gagal: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

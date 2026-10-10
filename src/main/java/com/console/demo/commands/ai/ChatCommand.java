package com.console.demo.commands.ai;

import com.console.demo.design.ColorsDesign;
import com.console.demo.dto.ChatChunk;
import com.console.demo.dto.ChatMessage;
import com.console.demo.dto.ChatRequest;
import com.console.demo.services.ConversationService;
import com.console.demo.services.MessageService;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import java.util.stream.Collectors;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class ChatCommand {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final long CHAR_DELAY_MS = 12;
    private final RestClient restClient;
    private final LineReader lineReader;
    private final String defaultModel;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private Boolean saveFirstChat = true;
    private Long idConversation;

    public ChatCommand(@Value("${spring.ai.openai.chat.model}") String defaultModel, RestClient restClient, LineReader lineReader, ConversationService conversationService, MessageService messageService) {
        this.defaultModel = defaultModel;
        this.restClient = restClient;
        this.lineReader = lineReader;
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    @Command(
            name = "chat",
            description = "Mulai Percakapan",
            group = "Router",
            help = "Mulai percakapan dengan combo 9Router anda",
            alias = {"cht"}
    )
    public String chat(@Option(shortName = 'm', longName = "model",
            description = "Model yang dipakai") String model) {

        model = (model == null || model.isBlank()) ? defaultModel : model;

        // ambil semua history dari postgres
        List<ChatMessage> history = messageService.getAll().stream()
                .map(m -> new ChatMessage(m.role(), m.content()))
                .collect(Collectors.toCollection(ArrayList::new));

        System.out.println(ColorsDesign.gold("Mode chat (" + model + "). Ketik /exit untuk keluar, /clear untuk reset."));

        while (true) {
            String input;
            try {
                input = lineReader.readLine(ColorsDesign.gold("you ❯ "));
            } catch (UserInterruptException | EndOfFileException e) {
                break;
            }

            if (input.isBlank()) continue;
            if (input.equals("/exit")) break;
            if (input.equals("/clear")) {
                history.clear();
                System.out.println(ColorsDesign.gold("Riwayat dihapus."));
                continue;
            }

            history.add(new ChatMessage("user", input));

            // simpan chat pertama kali
            while(saveFirstChat){
                idConversation = conversationService.add(input).getId();
                saveFirstChat = false;
            }
            // menyimpan pesan dari user
            messageService.add(idConversation, "user", input);

            try {
                String reply = streamReply(model, history);
                if (reply.isEmpty()) {
                    history.remove(history.size() - 1);
                    System.out.println(ColorsDesign.red("✘ Respons kosong dari server"));
                } else {
                    history.add(new ChatMessage("assistant", reply));
                    // balasan dari ai kita simpan juga
                    messageService.add(idConversation, "assistant", reply);
                }
            } catch (Exception e) {
                history.remove(history.size() - 1);
                System.out.println();
                System.out.println(ColorsDesign.red("✘ " + e.getMessage()));
            }
        }
        return ColorsDesign.gold("Percakapan selesai.");
    }

    private void typewrite(String text) {
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            System.out.print(new String(Character.toChars(cp)));
            System.out.flush();

            if (CHAR_DELAY_MS <= 0 || cp == '\n') continue;

            long delay = CHAR_DELAY_MS;
            if (cp == '.' || cp == '!' || cp == '?') delay *= 8;   // jeda di akhir kalimat
            else if (cp == ',' || cp == ';' || cp == ':') delay *= 4;

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.print(text.substring(i));   // tampilkan sisanya sekaligus
                return;
            }


        }
    }

    private String streamReply(String model, List<ChatMessage> history) {
        StringBuilder full = new StringBuilder();
        System.out.print(ColorsDesign.gold("AI  ❯ "));
        System.out.flush();

        restClient.post()
                .uri("/v1/chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(new ChatRequest(model, history, true))
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException(
                                "Server membalas error " + response.getStatusCode().value());
                    }
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

                            typewrite(delta.content());
                            full.append(delta.content());
                        }
                    }
                    return null;
                });

        System.out.println();
        return full.toString();
    }
}

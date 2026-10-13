package com.console.demo.commands.ai;

import com.console.demo.design.ColorsDesign;
import com.console.demo.design.ThinkingSpinner;
import com.console.demo.services.*;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ChatCommand {

    private static final long CHAR_DELAY_MS = 12;

    // Pemicu rangkuman berdasarkan prompt_tokens asli dari server.
    // Angka ini SUDAH termasuk dasar gateway (sekitar 2.000-2.700 token).
    private static final int PROMPT_LIMIT = 6000;
    // Pesan mentah terakhir yang disisakan saat merangkum.
    private static final int KEEP_LAST = 6;
    // Perkiraan dasar gateway, dipakai hanya jika usage dari server kosong.
    private static final int GATEWAY_BASE = 2500;

    private final LineReader lineReader;
    private final String defaultModel;

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final SummaryService summaryService;
    private final SummarizerService summarizerService;

    private Boolean saveFirstChat = true;
    private Long idConversation;

    private final ChatClient chatClient;
    private Usage lastUsage;

    public ChatCommand(@Value("${spring.ai.openai.chat.model}") String defaultModel,
                       LineReader lineReader,
                       ConversationService conversationService,
                       MessageService messageService,
                       SummaryService summaryService,
                       SummarizerService summarizerService,
                       ChatClient.Builder chatClientBuilder) {
        this.defaultModel = defaultModel;
        this.lineReader = lineReader;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.summaryService = summaryService;
        this.summarizerService = summarizerService;
        this.chatClient = chatClientBuilder.build();
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

        final String activeModel = (model == null || model.isBlank()) ? defaultModel : model;

        // ringkasan terbaru + pesan yang belum dirangkum
        List<Message> history = new ArrayList<>(loadHistory());

        System.out.println(ColorsDesign.gold("Mode chat (" + activeModel + "). Ketik /exit untuk keluar, /clear untuk reset."));

        while (true) {
            System.out.println(divider());
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
            System.out.println(divider());

            // simpan chat pertama kali
            if (saveFirstChat) {
                String title = input.length() > 50 ? input.substring(0, 50) : input;
                idConversation = conversationService.add(title).getId();
                saveFirstChat = false;
            }
            history.add(new UserMessage(input));

            String reply;
            try {
                reply = streamReply(activeModel, history);
            } catch (Exception e) {
                history.remove(history.size() - 1);
                System.out.println();
                System.out.println(ColorsDesign.red("✘ " + e.getMessage()));
                continue;
            }

            if (reply == null || reply.isEmpty()) {
                history.remove(history.size() - 1);
                System.out.println(ColorsDesign.red("Respons kosong dari server"));
                continue;
            }

            history.add(new AssistantMessage(reply));
            messageService.add(idConversation, "user", input, estimate(input));
            messageService.add(idConversation, "assistant", reply, estimate(reply));

            printTokens(input, reply);
            compactIfNeeded(history);   // kegagalan merangkum tidak mengganggu chat
        }
        return ColorsDesign.gold("Percakapan selesai.");
    }

    private String streamReply(String model, List<Message> history) {
        StringBuilder full = new StringBuilder();
        ThinkingSpinner spinner =
                new ThinkingSpinner(lineReader.getTerminal(), "AI Sedang Berpikir").start();
        boolean[] first = {true};
        AtomicReference<Usage> usage = new AtomicReference<>();

        try {
            chatClient.prompt()
                    .options(ChatOptions.builder().model(model).temperature(0.7))
                    .messages(history)
                    .stream()
                    .chatResponse()
                    .doOnNext(r -> {
                        if (r.getMetadata() != null && r.getMetadata().getUsage() != null
                                && r.getMetadata().getUsage().getTotalTokens() != null
                                && r.getMetadata().getUsage().getTotalTokens() > 0) {
                            usage.set(r.getMetadata().getUsage());
                        }

                        if (r.getResult() == null || r.getResult().getOutput() == null) return;
                        String text = r.getResult().getOutput().getText();
                        if (text == null || text.isEmpty()) return;

                        if (first[0]) {
                            first[0] = false;
                            spinner.stop();
                            System.out.print(ColorsDesign.gold("AI  ❯ "));
                            System.out.flush();
                        }

                        typewrite(text);
                        full.append(text);
                    })
                    .blockLast();
        } finally {
            spinner.stop();
        }

        if (!first[0]) {
            System.out.println();
        }

        lastUsage = usage.get();
        return full.toString();
    }

    /**
     * Pemicu utama: prompt_tokens asli dari server.
     * Cadangan: estimasi dari teks jika usage kosong.
     */
    private void compactIfNeeded(List<Message> history) {
        Integer promptTokens = (lastUsage != null) ? lastUsage.getPromptTokens() : null;

        boolean need = (promptTokens != null)
                ? promptTokens > PROMPT_LIMIT
                : estimateHistory(history) + GATEWAY_BASE > PROMPT_LIMIT;
        if (!need) return;

        try {
            System.out.println(ColorsDesign.gold("[ringkasan] memadatkan riwayat lama..."));
            if (summarizerService.summarize(KEEP_LAST)) {
                history.clear();
                history.addAll(loadHistory());
                System.out.println(ColorsDesign.gold("[ringkasan] selesai"));
            } else {
                System.out.println(ColorsDesign.gold("[ringkasan] belum berhasil, dicoba lagi nanti"));
            }
        } catch (Exception e) {
            System.out.println(ColorsDesign.red("[ringkasan gagal, dicoba lagi nanti] " + e.getMessage()));
        }
    }

    private List<Message> loadHistory() {
        List<Message> h = new ArrayList<>();
        summaryService.getLatest().ifPresent(s ->
                h.add(new SystemMessage("Konteks percakapan sebelumnya (ringkasan):\n" + s.getContent())));
        messageService.getRecent().stream()
                .map(m -> toAiMessage(m.role(), m.content()))
                .forEach(h::add);
        return h;
    }

    private void printTokens(String question, String answer) {
        String line = String.format("[token] pertanyaan~%d | jawaban~%d",
                estimate(question), estimate(answer));

//        if (lastUsage != null) {
//            line += String.format(" | server: prompt=%d, jawaban=%d, total=%d",
//                    lastUsage.getPromptTokens(),
//                    lastUsage.getCompletionTokens(),
//                    lastUsage.getTotalTokens());
//        }
        System.out.println(ColorsDesign.gold(line));
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
            if (cp == '.' || cp == '!' || cp == '?') delay *= 8;
            else if (cp == ',' || cp == ';' || cp == ':') delay *= 4;

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.print(text.substring(i));
                return;
            }
        }
    }

    private String divider() {
        int width = lineReader.getTerminal().getWidth();
        if (width <= 0) width = 60;
        return ColorsDesign.gold("─".repeat(Math.min(width, 80)));
    }

    private Message toAiMessage(String role, String content) {
        if ("user".equalsIgnoreCase(role)) {
            return new UserMessage(content);
        } else if ("assistant".equalsIgnoreCase(role)) {
            return new AssistantMessage(content);
        }
        return new SystemMessage(content);
    }

    private int estimate(String s) {
        if (s == null || s.isEmpty()) return 0;
        return (int) Math.ceil(s.length() / 3.5);
    }

    private int estimateHistory(List<Message> history) {
        int total = 0;
        for (Message m : history) {
            total += estimate(m.getText());
        }
        return total;
    }
}
package com.console.demo.commands.ai;

import com.console.demo.design.ColorsDesign;
import com.console.demo.services.MessageService;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

@Component
public class VectorCommand {

    private final MessageService messageService;

    public VectorCommand(MessageService messageService) {
        this.messageService = messageService;
    }

    @Command(
            name = "backfill-vector",
            description = "Isi vector untuk pesan lama",
            group = "Router",
            help = "Buat embedding (bge-m3 via Ollama) untuk pasangan tanya-jawab yang belum punya vector"
    )
    public String backfillVector() {
        int total = messageService.backfillVectors(
                n -> System.out.println(ColorsDesign.gold("Embedded " + n + " pasangan...")));
        return ColorsDesign.gold("Selesai: " + total + " pasangan tanya-jawab diberi vector.");
    }
}
package com.console.demo.commands.ai;

import com.console.demo.design.ColorsDesign;
import com.console.demo.dto.ModelListResponse;
import org.springframework.shell.jline.tui.table.ArrayTableModel;
import org.springframework.shell.jline.tui.table.BorderStyle;
import org.springframework.shell.jline.tui.table.TableBuilder;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;

@Component
public class ListModelsCommand {
    private final RestClient restClient;
    public ListModelsCommand(RestClient restClient) {
        this.restClient = restClient;
    }

    @Command(
            name = "models",
            description = "Daftar model yang digunakan oleh combo",
            group = "Router",
            help = "Menampilkan semua model dari 9router dalam bentuk tabel. Usage: models",
            alias = {"model", "ls"},
            exitStatusExceptionMapper = "myCustomExceptionMapper",
            availabilityProvider = "routerAvailability"
    )
    public String models() {
        ModelListResponse response = restClient.get()
                .uri("/v1/models")
                .retrieve()
                .body(ModelListResponse.class);

        if (response == null || response.data() == null) {
            return "No models found";
        }
        List<ModelListResponse.ModelInfo> sorted = response.data().stream()
                .sorted(Comparator.comparing(ModelListResponse.ModelInfo::ownedBy)
                        .thenComparing(ModelListResponse.ModelInfo::id))
                .toList();

        Object[][] data = new Object[sorted.size() + 1][5];
        data[0] = new Object[]{"MODEL", "OWNER", "CONTEXT", "VISION", "REASONING"};

        for (int i = 0; i < sorted.size(); i++) {
            var m = sorted.get(i);
            var c = m.capabilities();
            data[i + 1] = new Object[]{
                    m.id(),
                    m.ownedBy(),
                    m.contextLength() == null ? "-" : m.contextLength(),
                    c != null && c.vision() ? "Ya" : "-",
                    c != null && c.reasoning() ? "Ya" : "-"
            };
        }

        TableBuilder builder = new TableBuilder(new ArrayTableModel(data));
        builder.addHeaderBorder(BorderStyle.fancy_light);
        builder.addInnerBorder(BorderStyle.fancy_light);
        builder.addOutlineBorder(BorderStyle.fancy_light);
        String table = builder.build().render(120);
        table = ColorsDesign.gold(table)
                .replace("✔", ColorsDesign.green("✔"))
                .replace("✘", ColorsDesign.red("✘"));
       return table;


    }
}

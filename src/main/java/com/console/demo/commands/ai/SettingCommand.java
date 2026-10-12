package com.console.demo.commands.ai;

import com.console.demo.design.ColorsDesign;
import com.console.demo.dto.Setting9Router;
import com.console.demo.entities.Setting9RouterEntity;
import com.console.demo.services.Setting9RouterService;
import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.util.List;

@Component
public class SettingCommand {

    private final LineReader lineReader;
    private final Setting9RouterService service;

    public SettingCommand(LineReader lineReader, Setting9RouterService service) {
        this.lineReader = lineReader;
        this.service = service;
    }

    @Command(
            name = "setting",
            description = "Setting api url, key, dan combo 9Router",
            group = "Router",
            help = "Setting api url, key, dan combo 9Router",
            exitStatusExceptionMapper = "myCustomExceptionMapper",
            availabilityProvider = "routerAvailability"
    )
    public String setting() {
        System.out.println(divider());
        try {
            List<Setting9RouterEntity> list = service.showAll();
            Setting9RouterEntity current = list.isEmpty() ? null : list.get(0);

            if (current == null) {
                System.out.println(ColorsDesign.gold("Belum ada data setting."));
            } else {
                System.out.println(ColorsDesign.gold("Data saat ini:"));
                System.out.println(ColorsDesign.gold("  Api URL    : " + current.getBaseUrl()));
                System.out.println(ColorsDesign.gold("  Api Key    : " + mask(current.getApiKey())));
                System.out.println(ColorsDesign.gold("  Combo Name : " + current.getComboName()));
            }
            System.out.println(divider());

            int pilihan = selectMenu("Pilih menu (↑/↓ lalu Enter, q untuk batal)",
                    List.of("Add", "Update", "Delete"));

            return switch (pilihan) {
                case 0 -> doAdd(current);
                case 1 -> doUpdate(current);
                case 2 -> doDelete(current);
                default -> ColorsDesign.gold("Dibatalkan.");
            };
        } catch (UserInterruptException | EndOfFileException e) {
            return ColorsDesign.gold("Dibatalkan.");
        }
    }

    private String doAdd(Setting9RouterEntity current) {
        if (current != null) {
            return ColorsDesign.gold("Data sudah ada. Tidak bisa menambah lebih dari satu, silakan pilih Update.");
        }
        Setting9Router dto = readForm(null);
        System.out.println(divider());
        try {
            Setting9RouterEntity saved = service.add(dto);
            return ColorsDesign.gold("Berhasil menambah data");
        } catch (RuntimeException e) {
            return ColorsDesign.gold("Gagal: " + e.getMessage());
        }
    }

    private String doUpdate(Setting9RouterEntity current) {
        if (current == null) {
            return ColorsDesign.gold("Belum ada data. Silakan pilih Add terlebih dahulu.");
        }
        Setting9Router dto = readForm(current);
        System.out.println(divider());
        try {
            service.update(current.getId(), dto);
            return ColorsDesign.gold("Berhasil mengubah data.");
        } catch (RuntimeException e) {
            return ColorsDesign.gold("Gagal: " + e.getMessage());
        }
    }

    private String doDelete(Setting9RouterEntity current) {
        if (current == null) {
            return ColorsDesign.gold("Belum ada data untuk dihapus.");
        }
        String jawab = lineReader.readLine(ColorsDesign.gold("Yakin hapus data ini? (y/N) : "));
        if (jawab == null || !jawab.trim().equalsIgnoreCase("y")) {
            return ColorsDesign.gold("Dibatalkan.");
        }
        System.out.println(divider());
        try {
            service.delete(current.getId());
            return ColorsDesign.gold("Berhasil menghapus data.");
        } catch (RuntimeException e) {
            return ColorsDesign.gold("Gagal: " + e.getMessage());
        }
    }


    private Setting9Router readForm(Setting9RouterEntity current) {
        System.out.println(divider());
        String apiUrl = ask("Api URL : ", "http://localhost:20128/v1",
                current == null ? null : current.getBaseUrl(), null);
        System.out.println(divider());
        String apiKey = ask("Api Key : ", "sk-xxxxxxxxxxxxxxx",
                current == null ? null : current.getApiKey(), null);
        System.out.println(divider());
        String combo = ask("Combo Name : ", "Nama Combo 9Router yang mau anda gunakan",
                current == null ? null : current.getComboName(), null);

        Setting9Router dto = new Setting9Router();
        dto.setApiUrl(apiUrl);
        dto.setApiKey(apiKey);
        dto.setComboName(combo);
        return dto;
    }

    private String ask(String label, String placeholder, String initial, Character mask) {
        String ph = new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT + AttributedStyle.BLACK))
                .append(placeholder)
                .toAnsi();
        return lineReader.readLine(ColorsDesign.gold(label), ph, mask, initial);
    }

    // ===================== MENU PILIHAN =====================
    private int selectMenu(String title, List<String> items) {
        Terminal terminal = lineReader.getTerminal();
        PrintWriter out = terminal.writer();
        Attributes original = terminal.enterRawMode();
        try {
            KeyMap<String> keys = new KeyMap<>();
            keys.bind("up", "\033[A", "\033OA", "k");
            keys.bind("down", "\033[B", "\033OB", "j");
            keys.bind("enter", "\r", "\n");
            keys.bind("quit", "q", "\003");

            BindingReader reader = new BindingReader(terminal.reader());
            int selected = 0;

            out.println(ColorsDesign.gold(title));
            render(out, items, selected, false);

            while (true) {
                String op = reader.readBinding(keys);
                if (op == null || op.equals("quit")) return -1;
                if (op.equals("enter")) return selected;
                if (op.equals("up")) selected = (selected - 1 + items.size()) % items.size();
                if (op.equals("down")) selected = (selected + 1) % items.size();
                render(out, items, selected, true);
            }
        } finally {
            terminal.setAttributes(original);
            out.println();
        }
    }

    private void render(PrintWriter out, List<String> items, int selected, boolean redraw) {
        if (redraw) {
            out.print("\033[" + items.size() + "A"); // naik ke baris pertama menu
        }
        for (int i = 0; i < items.size(); i++) {
            out.print("\033[2K\r"); // hapus baris
            String line = (i + 1) + ". " + items.get(i);
            if (i == selected) {
                out.println(ColorsDesign.gold("  > " + line));
            } else {
                out.println("    " + line);
            }
        }
        out.flush();
    }



    private String mask(String key) {
        if (key == null || key.length() <= 4) return "****";
        return key.substring(0, 4) + "****";
    }

    private String divider() {
        int width = lineReader.getTerminal().getWidth();
        if (width <= 0) width = 60;
        return ColorsDesign.gold("─".repeat(Math.min(width, 80)));
    }
}
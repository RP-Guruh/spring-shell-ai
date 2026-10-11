package com.console.demo.commands.ai;

import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;
import org.jline.reader.LineReader;
import com.console.demo.design.ColorsDesign;
import org.jline.reader.UserInterruptException;
import org.jline.reader.EndOfFileException;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

@Component
public class SettingCommand {

    private final LineReader lineReader;
    private String apiUrl, apiKey, combo;

    public SettingCommand(LineReader lineReader) {
        this.lineReader = lineReader;
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
            String placeholderApiUrl = new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT + AttributedStyle.BLACK))
                    .append("http://localhost:20128/v1")
                    .toAnsi();

            apiUrl = lineReader.readLine(
                    ColorsDesign.gold("Api URL : "),
                    placeholderApiUrl,
                    (Character) null,
                    null
            );
            System.out.println(divider());
            String placeholderApiKey = new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT + AttributedStyle.BLACK))
                    .append("sk-xxxxxxxxxxxxxxx")
                    .toAnsi();

            apiKey = lineReader.readLine(
                    ColorsDesign.gold("Api Key : "),
                    placeholderApiKey,
                    (Character) null,
                    null
            );
            System.out.println(divider());
            String placeholderCombo = new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT + AttributedStyle.BLACK))
                    .append("Nama Combo 9Router yang mau anda gunakan")
                    .toAnsi();

            combo = lineReader.readLine(
                    ColorsDesign.gold("Combo Name : "),
                    placeholderCombo,
                    (Character) null,
                    null
            );

        } catch (UserInterruptException | EndOfFileException e) {
            return ColorsDesign.gold("Dibatalkan.");
        }

        return ColorsDesign.gold("Api Key : "+apiUrl+" Api Key : "+apiKey+" Combo : "+combo);
    }

    private String divider() {
        int width = lineReader.getTerminal().getWidth();
        if (width <= 0) width = 60;
        return ColorsDesign.gold("─".repeat(Math.min(width, 80)));
    }
}

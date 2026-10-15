package com.console.demo.design;

import org.jline.terminal.Terminal;

public final class BannerDesign {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GOLD = rgb(255, 190, 0);
    private static final int MIN_PANEL = 40;

    private static final String LOGO = """
             █████╗ ██████╗  ██████╗ ██╗   ██╗████████╗███████╗██████╗      ██████╗██╗  ██╗ █████╗ ████████╗
            ██╔══██╗██╔══██╗██╔═══██╗██║   ██║╚══██╔══╝██╔════╝██╔══██╗    ██╔════╝██║  ██║██╔══██╗╚══██╔══╝
            ╚██████║██████╔╝██║   ██║██║   ██║   ██║   █████╗  ██████╔╝    ██║     ███████║███████║   ██║  \s
             ╚═══██║██╔══██╗██║   ██║██║   ██║   ██║   ██╔══╝  ██╔══██╗    ██║     ██╔══██║██╔══██║   ██║  \s
             █████╔╝██║  ██║╚██████╔╝╚██████╔╝   ██║   ███████╗██║  ██║    ╚██████╗██║  ██║██║  ██║   ██║  \s
             ╚════╝ ╚═╝  ╚═╝ ╚═════╝  ╚═════╝    ╚═╝   ╚══════╝╚═╝  ╚═╝     ╚═════╝╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝  \s
            """;

    private static final int LOGO_WIDTH = LOGO.lines().mapToInt(String::length).max().orElse(0);

    private BannerDesign() {}

    public static String row(String label, String value) {
        return String.format("%-7s: %s", label, value);
    }

    public static void print(Terminal terminal, String title, String... infoLines) {
        if (Terminal.TYPE_DUMB.equals(terminal.getType())) {
            System.out.println(title);
            for (String line : infoLines) System.out.println(line);
            return;
        }

        System.out.print("\u001B[2J\u001B[H");
        System.out.flush();

        int width = terminal.getWidth() > 0 ? terminal.getWidth() : 80;

        if (width >= LOGO_WIDTH) {
            printLogo();
        } else {
            System.out.println(GOLD + BOLD + title + RESET);
        }

        System.out.println();
        printPanel(title, infoLines, width);
        System.out.println();
    }

    private static void printLogo() {
        var lines = LOGO.lines().toList();

        for (int i = 0; i < lines.size(); i++) {
            double t = lines.size() == 1 ? 0 : (double) i / (lines.size() - 1);
            int r = (int) (255 + (200 - 255) * t);
            int g = (int) (215 + (130 - 215) * t);
            int b = (int) (40 * t);
            System.out.println(rgb(r, g, b) + lines.get(i) + RESET);
        }
    }

    private static void printPanel(String title, String[] infoLines, int termWidth) {
        int longest = 0;
        for (String line : infoLines) longest = Math.max(longest, line.length());

        int inner = Math.max(MIN_PANEL, Math.max(longest + 2, title.length() + 6));
        inner = Math.min(inner, termWidth - 2);

        String head = " " + title + " ";
        int left = Math.max(0, (inner - head.length()) / 2);
        int right = Math.max(0, inner - head.length() - left);

        System.out.println(GOLD + "┌" + "─".repeat(left) + BOLD + head + RESET + GOLD
                + "─".repeat(right) + "┐" + RESET);

        for (String line : infoLines) {
            String content = " " + line;
            if (content.length() > inner) {
                content = content.substring(0, inner - 1) + "…";
            }
            System.out.println(GOLD + "│" + RESET
                    + String.format("%-" + inner + "s", content)
                    + GOLD + "│" + RESET);
        }

        System.out.println(GOLD + "└" + "─".repeat(inner) + "┘" + RESET);
    }

    private static String rgb(int r, int g, int b) {
        return "\u001B[38;2;" + r + ";" + g + ";" + b + "m";
    }
}
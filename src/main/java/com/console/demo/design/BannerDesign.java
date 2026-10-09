package com.console.demo.design;

public final class BannerDesign {
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String LOGO = """
             ███  ████   ███  █   █ █████ █████ ████      ███  █   █  ███  █████  \s
            █ ░░█ █░░░█ █ ░░█ █░  █░ ░█░░░█░░░░░█░░░█    █ ░░░ █░  █░█ ░░█  ░█░░░ \s
             ████░████░░█░ ░█░█░░ █░░ █░░░████░░████░░   █░ ░░░█████░█████░  █░░░░\s
              ░░█░█░░█░ █░░ █░█░░ █░░ █░░ █░░░░ █░░█░ ░  █░░   █░░░█░█░░░█░░ █░░  \s
             ███░░█░░░█░ ███ ░░███ ░░ █░░ █████░█░░░█░    ███  █░░░█░█░░░█░░ █░░  \s
              ░░░ ░░░  ░  ░░░ ░ ░░░ ░  ░░  ░░░░░ ░░  ░     ░░░  ░░  ░░░░  ░░  ░░  \s
               ░░░  ░   ░  ░░░   ░░░    ░   ░░░░░ ░   ░     ░░░  ░   ░ ░   ░   ░  \s""";
    private BannerDesign() {}

    public static void print(String title, String... infoLines) {
        if (System.console() == null) {
            // output diarahkan ke file/pipe: jangan cetak kode warna
            System.out.println(title);
            return;
        }
        printLogo();
        System.out.println();
        printPanel(title, infoLines);
        System.out.println();
    }

    // Logo dengan gradasi emas -> oranye, per baris
    private static void printLogo() {
        String[] lines = LOGO.split("\n");
        for (int i = 0; i < lines.length; i++) {
            double t = lines.length == 1 ? 0 : (double) i / (lines.length - 1);
            int r = (int) (255 + (200 - 255) * t);
            int g = (int) (215 + (130 - 215) * t);
            int b = (int) (0 + (40 - 0) * t);
            System.out.println(rgb(r, g, b) + lines[i] + RESET);
        }
    }

    // Kotak dengan judul di garis atas
    private static void printPanel(String title, String[] infoLines) {
        int width = 70;
        String gold = rgb(255, 190, 0);
        String head = " " + title + " ";
        int left = (width - head.length()) / 2;
        int right = width - head.length() - left;

        System.out.println(gold + "┌" + "─".repeat(left) + BOLD + head + RESET + gold
                + "─".repeat(right) + "┐" + RESET);

        for (String line : infoLines) {
            String content = " " + line;
            if (content.length() > width) {
                content = content.substring(0, width);
            }
            String padded = String.format("%-" + width + "s", content);
            System.out.println(gold + "│" + RESET + padded + gold + "│" + RESET);
        }

        System.out.println(gold + "└" + "─".repeat(width) + "┘" + RESET);
    }

    private static String rgb(int r, int g, int b) {
        return "\u001B[38;2;" + r + ";" + g + ";" + b + "m";
    }
}

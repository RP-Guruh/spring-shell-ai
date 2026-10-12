package com.console.demo.design;

import org.jline.terminal.Terminal;

import java.io.PrintWriter;

public class ThinkingSpinner implements AutoCloseable {

    private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    // Kalau terminal tidak menampilkan karakter di atas, pakai: {"|", "/", "-", "\\"}

    private final Terminal terminal;
    private final PrintWriter out;
    private final String text;
    private volatile boolean running = false;
    private Thread thread;

    public ThinkingSpinner(Terminal terminal, String text) {
        this.terminal = terminal;
        this.out = terminal.writer();
        this.text = text;
    }

    public ThinkingSpinner start() {
        if (running) return this;
        running = true;
        out.print("\033[?25l"); // sembunyikan kursor
        out.flush();

        thread = new Thread(() -> {
            int i = 0;
            while (running) {
                // titik animasi 0..3, dipad agar panjang teks tetap
                String dots = ".".repeat(i % 4);
                String label = String.format("%-3s", dots);
                String visible = FRAMES[i % FRAMES.length] + " " + text + label;

                int width = terminal.getWidth();
                if (width <= 0) width = 80;
                int pad = Math.max(0, (width - visible.length()) / 2);

                out.print("\r\033[2K" + " ".repeat(pad) + ColorsDesign.gold(visible));
                out.flush();

                i++;
                try {
                    Thread.sleep(90);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "thinking-spinner");
        thread.setDaemon(true);
        thread.start();
        return this;
    }

    public void stop() {
        if (!running) return;
        running = false;
        try {
            thread.join(300);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        out.print("\r\033[2K");  // hapus baris spinner
        out.print("\033[?25h");  // tampilkan kursor lagi
        out.flush();
    }

    @Override
    public void close() {
        stop();
    }
}
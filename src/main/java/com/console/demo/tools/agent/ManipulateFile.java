package com.console.demo.tools.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class ManipulateFile {

    // @path.txt atau @"path dengan spasi", harus diawali spasi/awal baris (email tidak ikut)
    private static final Pattern MENTION = Pattern.compile("(?<!\\S)@(?:\"([^\"]+)\"|(\\S+))");
    private static final long MAX_READ = 100_000;

    private final Set<Path> granted = ConcurrentHashMap.newKeySet();

    // default: tolak semua, jadi kalau lupa dipasang tidak ada aksi yang lolos
    private volatile BiPredicate<String, String> approver = (action, detail) -> false;

    public void setApprover(BiPredicate<String, String> approver) {
        this.approver = approver;
    }

    /** Panggil di chat loop sebelum prompt dikirim ke model. */
    public String expandMentions(String prompt) {
        Matcher m = MENTION.matcher(prompt);
        StringBuilder attach = new StringBuilder();

        while (m.find()) {
            String raw = m.group(1) != null ? m.group(1) : m.group(2);
            Path p = toPath(raw);

            // buang tanda baca di ujung, misal "@a.txt," atau "@a.txt?"
            if (!Files.exists(p)) {
                p = toPath(raw.replaceAll("[,.;:!?)]+$", ""));
            }
            if (!Files.exists(p)) {
                attach.append("\n[@").append(raw).append(" tidak ditemukan]\n");
                continue;
            }

            granted.add(p);
            System.out.println("  + dilampirkan: " + p);
            attach.append("\n<file path=\"").append(p).append("\">\n")
                    .append(Files.isDirectory(p) ? list(p) : readSafe(p))
                    .append("\n</file>\n");
        }
        return attach.isEmpty() ? prompt : prompt + "\n" + attach;
    }

    @Tool(description = """
        Menampilkan isi sebuah direktori. Path bisa absolut atau relatif
        terhadap direktori kerja aplikasi.
        """)
    String listFiles(String dir) {
        try {
            Path p = toPath(dir);
            if (!allowed(p, "LIHAT FOLDER")) return "User menolak.";
            return list(p);
        } catch (Exception e) {
            return "Gagal: " + e.getMessage();
        }
    }

    @Tool(description = """
        Membaca isi file teks. Path bisa absolut atau relatif.
        File yang di-@ user sudah otomatis terlampir di prompt.
        """)
    String readFile(String path) {
        try {
            Path p = toPath(path);
            if (!allowed(p, "BACA FILE")) return "User menolak.";
            return readSafe(p);
        } catch (Exception e) {
            return "Gagal membaca " + path + ": " + e.getMessage();
        }
    }

    @Tool(description = """
        Membuat file baru di path mana pun, termasuk membuat folder induknya jika belum ada.
        Butuh persetujuan user. Gagal jika file sudah ada.
        Jangan mengarang hasil: jika file tidak berhasil dibuat, sampaikan apa adanya
        bahwa file tidak dibuat, beserta alasannya.
        """)
    String createFile(String path, String content) {
        try {
            Path p = toPath(path);
            if (Files.exists(p)) return "File sudah ada, gunakan editFile.";
            if (!approve("BUAT FILE " + p, content)) return "User menolak, file tidak dibuat.";

            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.writeString(p, content);
            return "File dibuat: " + p;
        } catch (Exception e) {
            return "Gagal membuat " + path + ": " + e.getMessage();
        }
    }

    @Tool(description = """
        Mengedit file dengan mengganti satu potongan teks. Butuh persetujuan user.
        oldText harus muncul tepat satu kali di dalam file.
        """)
    String editFile(String path, String oldText, String newText) {
        try {
            Path p = toPath(path);
            String current = Files.readString(p);

            int idx = current.indexOf(oldText);
            if (idx < 0) return "Teks lama tidak ditemukan.";
            if (current.indexOf(oldText, idx + 1) >= 0) return "Teks lama muncul lebih dari sekali, tulis lebih spesifik.";

            if (!approve("EDIT FILE " + p, "- " + oldText + "\n+ " + newText)) {
                return "User menolak, file tidak diubah.";
            }
            Files.writeString(p, current.replace(oldText, newText));
            return "File diubah: " + p;
        } catch (Exception e) {
            return "Gagal mengedit " + path + ": " + e.getMessage();
        }
    }

    @Tool(description = "Menghapus satu file (bukan folder). Butuh persetujuan user.")
    String deleteFile(String path) {
        try {
            Path p = toPath(path);
            if (!Files.isRegularFile(p)) return "Hanya file biasa yang bisa dihapus.";
            if (!approve("HAPUS FILE " + p, "Ukuran: " + Files.size(p) + " byte")) {
                return "User menolak, file tidak dihapus.";
            }
            Files.delete(p);
            return "File dihapus: " + p;
        } catch (Exception e) {
            return "Gagal menghapus " + path + ": " + e.getMessage();
        }
    }

    // --- helper ---

    // public static supaya MentionCompleter memakai aturan path yang sama (~, relatif, absolut)
    public static Path toPath(String raw) {
        String s = raw.trim();
        if (s.equals("~") || s.startsWith("~/")) {
            s = System.getProperty("user.home") + s.substring(1);
        }
        return Path.of(s).toAbsolutePath().normalize();
    }

    // sudah di-@ (atau berada di dalam folder yang di-@) -> langsung boleh, selain itu tanya user
    private boolean allowed(Path p, String action) {
        for (Path g : granted) {
            if (p.startsWith(g)) return true;
        }
        if (approve(action + " (di luar yang kamu @) " + p, "")) {
            granted.add(p);
            return true;
        }
        return false;
    }

    private String readSafe(Path p) {
        try {
            if (Files.size(p) > MAX_READ) return "[file terlalu besar, " + Files.size(p) + " byte]";
            return Files.readString(p);
        } catch (IOException e) {
            return "[gagal membaca: " + e.getMessage() + "]";
        }
    }

    private String list(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(f -> f.getFileName() + (Files.isDirectory(f) ? "/" : ""))
                    .sorted()
                    .reduce("", (a, b) -> a + b + "\n");
        } catch (IOException e) {
            return "[gagal membaca folder: " + e.getMessage() + "]";
        }
    }

    private boolean approve(String action, String detail) {
        return approver.test(action, detail);
    }
}
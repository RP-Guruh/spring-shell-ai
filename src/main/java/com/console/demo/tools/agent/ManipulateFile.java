package com.console.demo.tools.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import org.springframework.ai.content.Media;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ManipulateFile {

    private static final Pattern MENTION = Pattern.compile("(?<!\\S)@(?:\"([^\"]+)\"|(\\S+))");
    private static final long MAX_READ = 100_000;

    private final Set<Path> granted = ConcurrentHashMap.newKeySet();

    private volatile BiPredicate<String, String> approver = (action, detail) -> false;

    private static final Map<String, MimeType> IMAGE_TYPES = Map.of(
        "jpg",  MimeTypeUtils.IMAGE_JPEG,
        "jpeg", MimeTypeUtils.IMAGE_JPEG,
        "png",  MimeTypeUtils.IMAGE_PNG,
        "gif",  MimeTypeUtils.IMAGE_GIF,
        "webp", MimeType.valueOf("image/webp")
    );

    static MimeType imageMime(Path p) {
        String ext = StringUtils.getFilenameExtension(p.getFileName().toString());
        return ext == null ? null : IMAGE_TYPES.get(ext.toLowerCase());
    }

    public void setApprover(BiPredicate<String, String> approver) {
        this.approver = approver;
    }

    private Path resolveMention(Matcher m) {
        String raw = m.group(1) != null ? m.group(1) : m.group(2);
        Path p = toPath(raw);
        if (!Files.exists(p)) {
            p = toPath(raw.replaceAll("[,.;:!?)]+$", ""));
        }
        return Files.exists(p) ? p : null;
    }

    public String expandMentions(String prompt) {
        Matcher m = MENTION.matcher(prompt);
        StringBuilder attach = new StringBuilder();

        while (m.find()) {
            String raw = m.group(1) != null ? m.group(1) : m.group(2);
            Path p = resolveMention(m);

            if (p == null) {
                attach.append("\n[@").append(raw).append(" tidak ditemukan]\n");
                continue;
            }

            granted.add(p);
            System.out.println("  + dilampirkan: " + p);

            if (imageMime(p) != null) {
                attach.append("\n[gambar terlampir: ").append(p.getFileName()).append("]\n");
            } else {
                attach.append("\n<file path=\"").append(p).append("\">\n")
                        .append(Files.isDirectory(p) ? list(p) : readSafe(p))
                        .append("\n</file>\n");
            }
        }
        return attach.isEmpty() ? prompt : prompt + "\n" + attach;
    }

    public List<Media> extractImages(String input) {
        List<Media> result = new ArrayList<>();
        Matcher m = MENTION.matcher(input);
        while (m.find()) {
            try {
                Path p = resolveMention(m);
                MimeType mime = p == null ? null : imageMime(p);
                if (mime != null && Files.isRegularFile(p)) {
                    result.add(new Media(mime, new FileSystemResource(p)));
                }
            } catch (Exception ignored) {}
        }
        return result;
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
            if (imageMime(p) != null) {
                return "File ini gambar, tidak bisa dibaca sebagai teks. "
                     + "Jika user menyebutnya dengan @, gambar sudah terlampir di pesan. "
                     + "Jika belum, minta user menulis @" + path + " di pesannya.";
            }
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

    public static Path toPath(String raw) {
        String s = raw.trim();
        if (s.equals("~") || s.startsWith("~/")) {
            s = System.getProperty("user.home") + s.substring(1);
        }
        return Path.of(s).toAbsolutePath().normalize();
    }

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

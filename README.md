# 9Router CLI (Spring Shell AI)

CLI interaktif berbasis Spring Shell untuk chatting dengan OpenAI-compatible API (9Router / local LLM) langsung dari terminal. Dilengkapi fitur streaming output dan pencatatan history percakapan ke PostgreSQL.

## Fitur

- Chat interaktif langsung di terminal dengan typewriter effect
- Streaming response (SSE)
- Pilihan model dinamis lewat parameter `-m` atau default config
- Cek ketersediaan model (`models`) dan status server (`status`)
- Simpan riwayat percakapan ke database PostgreSQL

## Kebutuhan Sistem

- Java 17+
- PostgreSQL
- Server AI yang kompatibel dengan OpenAI API (misal: 9Router / vLLM / Ollama)

## Setup & Konfigurasi

1. Salin file template config:
   ```bash
   cp src/main/resources/application.properties.example src/main/resources/application.properties
   ```

2. Sesuaikan isi `src/main/resources/application.properties`:
   ```properties
   # Konfigurasi AI Server
   spring.ai.openai.api-key=YOUR_API_KEY
   spring.ai.openai.base-url=http://localhost:20128
   spring.ai.openai.chat.model=YOUR_COMBO

   # Konfigurasi Database
   spring.datasource.url=jdbc:postgresql://localhost:5432/YOUR_DB
   spring.datasource.username=postgres
   spring.datasource.password=YOUR_PASSWORD
   ```

## Menjalankan Aplikasi

Jalankan langsung dengan Maven Wrapper:

```bash
./mvnw spring-boot:run
```

Atau build ke JAR terlebih dahulu:

```bash
./mvnw clean package -DskipTests
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

## Penggunaan Command

| Command | Alias | Keterangan |
| :--- | :--- | :--- |
| `chat` | `cht` | Masuk mode chat interaktif (menggunakan default model) |
| `chat -m <model>` | `cht -m <model>` | Chat menggunakan model spesifik |
| `models` | `mdl` | Menampilkan daftar model yang tersedia di server |
| `status` | `sts` | Mengecek status koneksi ke router |
| `help` | | Melihat semua command yang tersedia |
| `exit` | | Keluar dari aplikasi |

Saat berada di dalam mode **chat**:
- Ketik `/clear` untuk mereset riwayat chat sesi saat ini.
- Ketik `/exit` untuk kembali ke menu shell utama.

## Arsitektur & Relasi Database

Aplikasi menggunakan PostgreSQL untuk menyimpan riwayat sesi, log pesan, dan memory compression (ringkasan percakapan).

### Skema & Relasi Entitas (ERD)

```mermaid
erDiagram
    conversation ||--o{ message : "1 : N (has many)"
    conversation ||--o{ summaries : "1 : N (has many)"

    conversation {
        bigint id PK
        varchar title
        timestamp created_at
    }

    message {
        bigint id PK
        bigint conversation_id FK
        varchar role
        text content
        integer total_token
        boolean is_summarized
        timestamp created_at
    }

    summaries {
        bigint id PK
        bigint conversation_id FK
        bigint last_message_id
        text content
        integer count_message
        timestamp created_at
    }
```

### Detail Tabel & Relasi

1. **`conversation` (`ConversationEntity`)**
   - Menyimpan sesi percakapan utama.
   - Kolom:
     - `id` (PK, BigInt Auto Increment)
     - `title` (Varchar, judul percakapan diambil dari input pertama user max 50 karakter)
     - `created_at` (Timestamp, waktu dibuat)
   - **Relasi**:
     - `1 : N` ke tabel `message` (Satu percakapan memiliki banyak pesan).
     - `1 : N` ke tabel `summaries` (Satu percakapan dapat memiliki riwayat ringkasan bertahap).

2. **`message` (`MessageEntity`)**
   - Menyimpan setiap interaksi chat (user maupun assistant).
   - Kolom:
     - `id` (PK, BigInt Auto Increment)
     - `conversation_id` (FK ke `conversation.id`, `ManyToOne`, Not Null)
     - `role` (Varchar: `"user"` atau `"assistant"`)
     - `content` (Text, isi pesan)
     - `total_token` (Integer, estimasi token dihitung dari perkiraan `ceil(karakter / 3.5)`)
     - `is_summarized` (Boolean, penanda apakah pesan sudah diproses ke dalam ringkasan)
     - `created_at` (Timestamp, waktu dibuat)

3. **`summaries` (`SummaryEntity`)**
   - Menyimpan hasil kompresi/ringkasan memori dari pesan-pesan lama.
   - Kolom:
     - `id` (PK, BigInt Auto Increment)
     - `conversation_id` (FK ke `conversation.id`, `ManyToOne`, Not Null)
     - `last_message_id` (BigInt, ID pesan terakhir yang tercakup dalam batch ringkasan ini)
     - `content` (Text, isi ringkasan terakumulasi)
     - `count_message` (Integer, jumlah pesan yang diringkas pada batch tersebut)
     - `created_at` (Timestamp, waktu dibuat)

---

## Logika & Alur Sistem

### 1. Alur Mode Chat (`ChatCommand`)
- **Inisialisasi Riwayat (History)**:
  Saat command `chat` dijalankan, context memory dibentuk dari:
  1. Ringkasan terbaru (`summaries`) jika ada, dimasukkan sebagai `SystemMessage`:
     `"Konteks percakapan sebelumnya (ringkasan): ..."`
  2. Seluruh pesan yang belum diringkas (`is_summarized = false`) dari database (`messageService.getRecent()`) agar alur dialog aktif tetap lengkap.
- **Penyimpanan Sesi & Pesan**:
  - Pada pesan pertama (`saveFirstChat`), entitas `conversation` dibuat dengan `title` dari input pertama (maksimal 50 karakter).
  - Pesan `user` dan `assistant` masing-masing dihitung estimasi tokennya (`estimate(text) = ceil(length / 3.5)`).
  - Kedua pesan disimpan ke tabel `message` lengkap dengan kolom `total_token`.
- **Streaming & Ekstraksi Token Usage**:
  - Interaksi ke AI menggunakan Spring AI `ChatClient` secara streaming dengan tampilan `ThinkingSpinner` dan efek ketik (typewriter).
  - Mengambil metadata token asli (`Usage`) dari response stream server (`promptTokens`, `completionTokens`, `totalTokens`).
  - Menampilkan ringkasan token di terminal: `[token] pertanyaan~X | jawaban~Y`.
- **Pemicu Pemadatan / Ringkasan (`compactIfNeeded`)**:
  - **Threshold**: Batas pemicu ringkasan adalah `PROMPT_LIMIT = 6000` token. Angka ini telah memperhitungkan baseline konteks gateway/system prompt router (~2.000–2.700 token).
  - **Pemicu Utama**: Jika `prompt_tokens` riil dari server melebihi 6.000 token (`promptTokens > PROMPT_LIMIT`).
  - **Pemicu Cadangan**: Jika usage server kosong, menggunakan estimasi token teks lokal: `estimateHistory(history) + GATEWAY_BASE (2500) > PROMPT_LIMIT`.
  - Jika ambang batas terlampaui, pemadatan dipicu via `summarizerService.summarize(KEEP_LAST)`.
  - Setelah ringkasan baru berhasil dibuat dan disimpan, context in-memory dibersihkan dan dimuat ulang (`loadHistory()`) dengan ringkasan terkini dan sisa pesan aktif.

### 2. Alur & Logika Ringkasan (`SummarizerService`)
Untuk menjaga ukuran prompt tetap hemat token tanpa kehilangan memori esensial:
- **Penyisihan Pesan Terakhir (`KEEP_LAST = 6`)**:
  Menyisakan minimal 6 pesan aktif terakhir agar obrolan paling mutakhir tidak langsung diringkas dan percakapan tetap berjalan mulus.
- **Penyelarasan Batas Giliran (Role Alignment)**:
  Titik potong pesan lama yang akan diringkas (`foldCount`) disesuaikan mundur jika perlu agar batasnya berhenti tepat sebelum giliran pesan `user`, mencegah pasangan tanya-jawab terbelah di tengah jalan.
- **Concurrency Guard**:
  Menggunakan `AtomicBoolean` (`running`) untuk memastikan hanya satu proses peringkasan yang berjalan dalam satu waktu.
- **Akumulasi Konteks**:
  Mengambil ringkasan terakhir (`oldSummary`) jika ada, digabungkan dengan kumpulan pesan lama yang akan dipadatkan, lalu dikirim ke AI summarizer (`summaryGenerate.generate()`).
- **Pembaruan Transaksional**:
  - Menyimpan akumulasi ringkasan baru ke tabel `summaries`.
  - Menandai kumpulan pesan lama tersebut dengan status `is_summarized = true`.

---

## Aturan Ringkasan (Summary Rules)

Pembuatan ringkasan dijalankan oleh modul `SummaryGenerate` dengan panduan sistem (system prompt) ketat:

- **Fokus Esensial**: Hanya mencatat fakta tentang user, preferensi, keputusan penting, tugas aktif, angka/nama/istilah kunci, dan pertanyaan yang belum terjawab.
- **Eliminasi Noise**: Basa-basi, sapaan pembuka/penutup, dan topik yang sudah selesai dieliminasi.
- **Format Padat**: Ditulis dalam format poin-poin ringkas, bukan kalimat utuh yang menyalin perkataan asli.
- **Resolusi Konflik**: Apabila ada informasi baru yang bertentangan dengan informasi pada ringkasan lama, gunakan informasi yang paling baru.
- **Penanganan Kode**: Tidak menyalin potongan kode; hanya mencatat tujuan kode serta nama file/fungsi yang relevan.
- **Batasan Panjang**: Maksimal **250 kata**; prioritaskan membuang rincian sekunder jika melebihi batas.
- **Bahasa**: Mengikuti bahasa percakapan yang digunakan.
- **Output Bersih**: Mengembalikan langsung isi ringkasan tanpa teks pengantar atau penutup.

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

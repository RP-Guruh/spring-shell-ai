package com.console.demo.tools.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class WeatherTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);

    private static final Locale ID = Locale.forLanguageTag("id-ID");
    private static final DateTimeFormatter API_DATE = DateTimeFormatter.ofPattern("yyyy-M-d");
    private static final int MAX_RANGE_DAYS = 14;

    private final RestClient weatherClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final int maxPastDays;
    private final int maxFutureOffset;

    public WeatherTool(
            RestClient.Builder builder,
            @Value("${weather.api-key}") String apiKey,
            @Value("${weather.max-past-days:7}") int maxPastDays,
            @Value("${weather.max-future-offset:2}") int maxFutureOffset
    ) {
        this.weatherClient = builder
                .baseUrl("https://api.weatherapi.com/v1")
                .build();
        this.apiKey = apiKey;
        this.maxPastDays = maxPastDays;
        this.maxFutureOffset = maxFutureOffset;
    }

    // =========================================================
    // TOOL: CUACA SEKARANG
    // =========================================================

    @Tool(description = """
        Cuaca SAAT INI (real-time) di suatu lokasi.
        Gunakan untuk: cuaca sekarang, suhu sekarang, apakah sedang hujan.
        """)
    public String getCurrentWeather(
            @ToolParam(description = "Nama kota atau lokasi, contoh: Manokwari, Jakarta, Tokyo")
            String lokasi
    ) {
        try {
            validateLocation(lokasi);

            JsonNode root = fetch("/current.json", lokasi, null, null);
            JsonNode cur = root.path("current");

            ObjectNode out = baseInfo(root);
            out.put("jenis_data", "cuaca_saat_ini");
            out.put("waktu_lokal", root.path("location").path("localtime").asText());
            copy(out, "diperbarui", cur, "last_updated");
            copy(out, "suhu_c", cur, "temp_c");
            copy(out, "suhu_f", cur, "temp_f");
            copy(out, "terasa_seperti_c", cur, "feelslike_c");
            copy(out, "terasa_seperti_f", cur, "feelslike_f");
            copy(out, "kelembapan_persen", cur, "humidity");
            copy(out, "angin_kph", cur, "wind_kph");
            copy(out, "curah_hujan_mm", cur, "precip_mm");
            copy(out, "tutupan_awan_persen", cur, "cloud");
            out.put("kondisi", cur.path("condition").path("text").asText());
            out.put("sumber", "WeatherAPI.com");

            return out.toPrettyString();
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    // =========================================================
    // TOOL: CUACA SATU HARI (KEMARIN / HARI INI / BESOK / LUSA)
    // =========================================================

    @Tool(description = """
        Ringkasan cuaca SATU HARI penuh (suhu rata-rata/min/maks, peluang hujan, kondisi)
        untuk kata waktu relatif: kemarin, hari ini, besok, lusa.
        JANGAN menghitung tanggal sendiri, cukup kirim offsetHari.
        offsetHari: -2 = kemarin lusa, -1 = kemarin, 0 = hari ini, 1 = besok, 2 = lusa.
        """)
    public String getWeatherByDay(
            @ToolParam(description = "Nama kota atau lokasi, contoh: Manokwari")
            String lokasi,
            @ToolParam(description = "Selisih hari dari hari ini di lokasi tersebut. -1 kemarin, 0 hari ini, 1 besok, 2 lusa")
            int offsetHari
    ) {
        return daily(lokasi, offsetHari, offsetHari);
    }

    // =========================================================
    // TOOL: CUACA RENTANG HARI (MINGGU LALU, 3 HARI KE DEPAN)
    // =========================================================

    @Tool(description = """
        Ringkasan cuaca PER HARI untuk rentang beberapa hari.
        Gunakan untuk: minggu lalu, 3 hari terakhir, 3 hari ke depan, dan sejenisnya.
        JANGAN menghitung tanggal sendiri, cukup kirim offset hari.
        Contoh: minggu lalu = offsetAwal -7, offsetAkhir -1.
        3 hari ke depan = offsetAwal 1, offsetAkhir 3 (jika didukung).
        """)
    public String getWeatherRange(
            @ToolParam(description = "Nama kota atau lokasi, contoh: Manokwari")
            String lokasi,
            @ToolParam(description = "Offset hari awal (inklusif). Contoh: -7")
            int offsetAwal,
            @ToolParam(description = "Offset hari akhir (inklusif), harus >= offsetAwal. Contoh: -1")
            int offsetAkhir
    ) {
        return daily(lokasi, offsetAwal, offsetAkhir);
    }

    // =========================================================
    // TOOL: CUACA PER JAM
    // =========================================================

    @Tool(description = """
        Cuaca PER JAM (suhu, peluang hujan, kondisi) untuk satu hari.
        Gunakan jika pengguna menanyakan jam tertentu, misalnya "besok jam 3 sore hujan tidak?".
        offsetHari: -1 = kemarin, 0 = hari ini, 1 = besok, 2 = lusa.
        Jangan menghitung tanggal sendiri.
        """)
    public String getHourlyWeather(
            @ToolParam(description = "Nama kota atau lokasi, contoh: Manokwari")
            String lokasi,
            @ToolParam(description = "Selisih hari dari hari ini di lokasi tersebut. -1 kemarin, 0 hari ini, 1 besok")
            int offsetHari
    ) {
        try {
            validateLocation(lokasi);
            validateOffsets(offsetHari, offsetHari);

            JsonNode current = fetch("/current.json", lokasi, null, null);
            LocalDate today = localToday(current);
            LocalDate date = today.plusDays(offsetHari);

            JsonNode forecastDay = loadDay(lokasi, date, offsetHari, null);

            ObjectNode out = baseInfo(current);
            out.put("jenis_data", "cuaca_per_jam");
            out.put("tanggal_hari_ini_lokasi", today.toString());
            out.put("tanggal", date.toString());
            out.put("hari", date.getDayOfWeek().getDisplayName(TextStyle.FULL, ID));
            putRelativeLabel(out, offsetHari);
            out.put("jenis", offsetHari < 0 ? "arsip" : "prakiraan");

            ArrayNode hours = out.putArray("per_jam");
            for (JsonNode h : forecastDay.path("hour")) {
                ObjectNode n = objectMapper.createObjectNode();
                String time = h.path("time").asText();
                n.put("jam", time.contains(" ") ? time.substring(time.indexOf(' ') + 1) : time);
                copy(n, "suhu_c", h, "temp_c");
                copy(n, "suhu_f", h, "temp_f");
                copy(n, "kelembapan_persen", h, "humidity");
                copy(n, "peluang_hujan_persen", h, "chance_of_rain");
                copy(n, "curah_hujan_mm", h, "precip_mm");
                n.put("kondisi", h.path("condition").path("text").asText());
                hours.add(n);
            }
            out.put("sumber", "WeatherAPI.com");

            return out.toPrettyString();
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    private String daily(String lokasi, int from, int to) {
        try {
            validateLocation(lokasi);
            validateOffsets(from, to);

            // Tanggal "hari ini" menurut zona waktu LOKASI, bukan server.
            JsonNode current = fetch("/current.json", lokasi, null, null);
            LocalDate today = localToday(current);

            ObjectNode out = baseInfo(current);
            out.put("jenis_data", "cuaca_harian");
            out.put("tanggal_hari_ini_lokasi", today.toString());
            ArrayNode days = out.putArray("hari");

            // Prakiraan cukup diambil satu kali untuk semua hari >= 0.
            JsonNode forecastRoot = null;
            if (to >= 0) {
                forecastRoot = fetch("/forecast.json", lokasi, null, to + 1);
            }

            for (int off = from; off <= to; off++) {
                LocalDate date = today.plusDays(off);
                JsonNode fd = loadDay(lokasi, date, off, forecastRoot);
                days.add(dayNode(fd, date, off));
            }

            out.put("sumber", "WeatherAPI.com");
            return out.toPrettyString();
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    private JsonNode loadDay(String lokasi, LocalDate date, int offset, JsonNode forecastRoot) {
        if (offset < 0) {
            JsonNode root = fetch("/history.json", lokasi, date.toString(), null);
            JsonNode fd = root.path("forecast").path("forecastday").path(0);
            if (fd.isMissingNode() || fd.isNull()) {
                throw new WeatherException("Data arsip untuk tanggal " + date + " tidak tersedia.");
            }
            return fd;
        }

        JsonNode root = forecastRoot != null
                ? forecastRoot
                : fetch("/forecast.json", lokasi, null, offset + 1);

        for (JsonNode fd : root.path("forecast").path("forecastday")) {
            if (date.toString().equals(fd.path("date").asText())) {
                return fd;
            }
        }
        throw new WeatherException("Data prakiraan untuk tanggal " + date + " tidak tersedia.");
    }

    private ObjectNode dayNode(JsonNode fd, LocalDate date, int offset) {
        JsonNode d = fd.path("day");
        ObjectNode n = objectMapper.createObjectNode();

        n.put("tanggal", date.toString());
        n.put("hari", date.getDayOfWeek().getDisplayName(TextStyle.FULL, ID));
        n.put("offset_hari", offset);
        putRelativeLabel(n, offset);
        n.put("jenis", offset < 0 ? "arsip" : "prakiraan");

        copy(n, "suhu_rata2_c", d, "avgtemp_c");
        copy(n, "suhu_min_c", d, "mintemp_c");
        copy(n, "suhu_maks_c", d, "maxtemp_c");
        copy(n, "suhu_rata2_f", d, "avgtemp_f");
        copy(n, "suhu_min_f", d, "mintemp_f");
        copy(n, "suhu_maks_f", d, "maxtemp_f");
        copy(n, "kelembapan_rata2_persen", d, "avghumidity");
        copy(n, "peluang_hujan_persen", d, "daily_chance_of_rain");
        copy(n, "total_curah_hujan_mm", d, "totalprecip_mm");
        copy(n, "angin_maks_kph", d, "maxwind_kph");
        n.put("kondisi", d.path("condition").path("text").asText());
        return n;
    }

    // =========================================================
    // HTTP REQUEST
    // =========================================================

    private JsonNode fetch(String endpoint, String lokasi, String dt, Integer days) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("key", apiKey);
        vars.put("q", lokasi.trim());
        if (dt != null) {
            vars.put("dt", dt);
        }
        if (days != null) {
            vars.put("days", days);
        }

        try {
            String body = weatherClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder
                                .path(endpoint)
                                .queryParam("key", "{key}")
                                .queryParam("q", "{q}")
                                .queryParam("lang", "id");
                        if (dt != null) {
                            uriBuilder.queryParam("dt", "{dt}");
                        }
                        if (days != null) {
                            uriBuilder.queryParam("days", "{days}");
                        }
                        return uriBuilder.build(vars);
                    })
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new WeatherException("WeatherAPI.com mengembalikan respons kosong.");
            }
            return objectMapper.readTree(body);

        } catch (RestClientResponseException e) {
            throw new WeatherException(describeApiError(e), e);
        } catch (WeatherException e) {
            throw e;
        } catch (RestClientException e) {
            throw new WeatherException("Tidak dapat menghubungi WeatherAPI.com.", e);
        } catch (Exception e) {
            throw new WeatherException("Gagal membaca respons WeatherAPI.com.", e);
        }
    }

    private String describeApiError(RestClientResponseException e) {
        String apiMessage = null;
        try {
            apiMessage = objectMapper.readTree(e.getResponseBodyAsString())
                    .path("error").path("message").asText(null);
        } catch (Exception ignored) {
            // body bukan JSON, abaikan
        }
        int status = e.getStatusCode().value();
        if (apiMessage != null && !apiMessage.isBlank()) {
            return "WeatherAPI.com menolak permintaan (HTTP " + status + "): " + apiMessage;
        }
        return "WeatherAPI.com gagal (HTTP " + status + ").";
    }

    // =========================================================
    // HELPER
    // =========================================================

    private LocalDate localToday(JsonNode currentRoot) {
        String localtime = currentRoot.path("location").path("localtime").asText("");
        if (localtime.isBlank()) {
            throw new WeatherException("Waktu lokal lokasi tidak tersedia dari API.");
        }
        // Format API: "2026-10-14 15:54" (jam kadang tanpa nol di depan)
        String datePart = localtime.split(" ")[0];
        try {
            return LocalDate.parse(datePart, API_DATE);
        } catch (Exception e) {
            throw new WeatherException("Format waktu lokal dari API tidak dikenali: " + localtime, e);
        }
    }

    private ObjectNode baseInfo(JsonNode root) {
        JsonNode loc = root.path("location");
        ObjectNode out = objectMapper.createObjectNode();
        out.put("status", "ok");
        out.put("lokasi", loc.path("name").asText());
        out.put("wilayah", loc.path("region").asText());
        out.put("negara", loc.path("country").asText());
        out.put("zona_waktu", loc.path("tz_id").asText());
        return out;
    }

    private void copy(ObjectNode dst, String dstName, JsonNode src, String srcName) {
        JsonNode value = src.path(srcName);
        if (!value.isMissingNode() && !value.isNull()) {
            dst.set(dstName, value);
        }
    }

    private void putRelativeLabel(ObjectNode node, int offset) {
        String label = switch (offset) {
            case -2 -> "kemarin lusa";
            case -1 -> "kemarin";
            case 0 -> "hari ini";
            case 1 -> "besok";
            case 2 -> "lusa";
            default -> null;
        };
        if (label != null) {
            node.put("keterangan", label);
        }
    }

    // =========================================================
    // VALIDASI
    // =========================================================

    private void validateLocation(String lokasi) {
        if (lokasi == null || lokasi.isBlank()) {
            throw new WeatherException("Parameter lokasi wajib diisi (nama kota).");
        }
    }

    private void validateOffsets(int from, int to) {
        if (from > to) {
            throw new WeatherException("offsetAwal tidak boleh lebih besar dari offsetAkhir.");
        }
        if (from < -maxPastDays) {
            throw new WeatherException(
                    "Data arsip hanya tersedia sampai " + maxPastDays + " hari ke belakang "
                            + "(offset terkecil -" + maxPastDays + ").");
        }
        if (to > maxFutureOffset) {
            throw new WeatherException(
                    "Prakiraan hanya tersedia sampai " + maxFutureOffset + " hari ke depan "
                            + "(offset terbesar " + maxFutureOffset + ").");
        }
        if ((long) to - from + 1 > MAX_RANGE_DAYS) {
            throw new WeatherException("Rentang maksimal " + MAX_RANGE_DAYS + " hari per permintaan.");
        }
    }

    // =========================================================
    // ERROR UNTUK MODEL (JSON, BUKAN EXCEPTION)
    // =========================================================

    private String errorJson(Exception e) {
        if (e instanceof WeatherException) {
            log.warn("Tool cuaca gagal: {}", e.getMessage());
        } else {
            log.error("Tool cuaca error tak terduga", e);
        }

        String message = (e instanceof WeatherException)
                ? e.getMessage()
                : "Terjadi kesalahan tak terduga saat mengambil data cuaca.";

        ObjectNode out = objectMapper.createObjectNode();
        out.put("status", "error");
        out.put("pesan", message);
        out.put("instruksi",
                "Sampaikan ke pengguna bahwa data cuaca tidak tersedia beserta alasannya. "
                        + "Jangan menebak, mengarang, atau mengganti dengan data lain.");
        return out.toPrettyString();
    }

    private static class WeatherException extends RuntimeException {
        WeatherException(String message) {
            super(message);
        }

        WeatherException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
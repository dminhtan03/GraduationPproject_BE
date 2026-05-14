package com.finalProject.BookingMeetingRoom.service.aiplatform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finalProject.BookingMeetingRoom.common.config.AiPlatformProperties;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.RawTranscript;
import com.finalProject.BookingMeetingRoom.model.response.aiplatform.pipeline.TranscriptSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiSttService {

    private final AiPlatformProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String transcribe(Path audioPath, String language) {
        if (audioPath == null || !Files.exists(audioPath)) {
            log.warn("STT audio file missing: {}", audioPath);
            return null;
        }
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            log.warn("STT skipped: API key missing");
            return null;
        }

        try {
            String boundary = "----AiBoundary" + UUID.randomUUID();
            byte[] body = buildMultipartBody(boundary, audioPath, language);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(props.getBaseUrl()) + "/audio/transcriptions"))
                    .timeout(Duration.ofMillis(props.getSttTimeoutMs()))
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("STT failed: status={} body={}", response.statusCode(), response.body());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode text = root.path("text");
            return text.isTextual() ? text.asText().trim() : null;
        } catch (Exception ex) {
            log.warn("STT failed: {}", ex.getMessage());
            return null;
        }
    }

    public RawTranscript transcribeRaw(Path audioPath, String language) {
        if (audioPath == null || !Files.exists(audioPath)) {
            log.warn("STT audio file missing: {}", audioPath);
            return RawTranscript.builder().segments(List.of()).language(language).durationSeconds(0.0).build();
        }
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            log.warn("STT skipped: API key missing");
            return RawTranscript.builder().segments(List.of()).language(language).durationSeconds(0.0).build();
        }

        try {
            String boundary = "----AiBoundary" + UUID.randomUUID();
            byte[] body = buildMultipartBody(boundary, audioPath, language, true);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(props.getBaseUrl()) + "/audio/transcriptions"))
                    .timeout(Duration.ofMillis(props.getSttTimeoutMs()))
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("STT failed: status={} body={}", response.statusCode(), response.body());
                return RawTranscript.builder().segments(List.of()).language(language).durationSeconds(0.0).build();
            }

            JsonNode root = objectMapper.readTree(response.body());
            List<TranscriptSegment> segments = new ArrayList<>();
            JsonNode segs = root.get("segments");
            if (segs != null && segs.isArray()) {
                for (JsonNode seg : segs) {
                    String text = seg.path("text").asText("").trim();
                    if (text.isBlank()) {
                        continue;
                    }
                    double start = seg.path("start").asDouble(0.0);
                    double end = seg.path("end").asDouble(start);
                    double avgLogprob = seg.path("avg_logprob").asDouble(-0.3);
                    double conf = Math.exp(avgLogprob);
                    TranscriptSegment out = TranscriptSegment.builder()
                            .segmentId(UUID.randomUUID().toString())
                            .speaker("UNKNOWN")
                            .start(start)
                            .end(end)
                            .text(text)
                            .sttConfidence(conf)
                            .confidence(conf)
                            .needsReview(false)
                            .build();
                    segments.add(out);
                }
            }

            double duration = root.path("duration").asDouble(segments.isEmpty() ? 0.0 : segments.get(segments.size() - 1).getEnd());
            String lang = root.path("language").asText(language != null ? language : "vi");
            return RawTranscript.builder()
                    .segments(segments)
                    .language(lang)
                    .durationSeconds(duration)
                    .build();
        } catch (Exception ex) {
            log.warn("STT failed: {}", ex.getMessage());
            return RawTranscript.builder().segments(List.of()).language(language).durationSeconds(0.0).build();
        }
    }

    private byte[] buildMultipartBody(String boundary, Path audioPath, String language) throws IOException {
        return buildMultipartBody(boundary, audioPath, language, false);
    }

    private byte[] buildMultipartBody(String boundary, Path audioPath, String language, boolean verbose) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String newline = "\r\n";

        writeTextPart(out, boundary, "model", "whisper-1", newline);
        if (language != null && !language.isBlank()) {
            writeTextPart(out, boundary, "language", language, newline);
        }
        if (verbose) {
            writeTextPart(out, boundary, "response_format", "verbose_json", newline);
            writeTextPart(out, boundary, "timestamp_granularities[]", "segment", newline);
        }

        String fileName = audioPath.getFileName().toString();
        out.write(("--" + boundary + newline).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + newline)
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: application/octet-stream" + newline + newline).getBytes(StandardCharsets.UTF_8));
        Files.copy(audioPath, out);
        out.write(newline.getBytes(StandardCharsets.UTF_8));

        out.write(("--" + boundary + "--" + newline).getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private void writeTextPart(ByteArrayOutputStream out, String boundary, String name, String value, String newline) throws IOException {
        out.write(("--" + boundary + newline).getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"" + newline + newline)
                .getBytes(StandardCharsets.UTF_8));
        out.write((value + newline).getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1";
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}

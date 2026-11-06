package services;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SpeechToText} backed by <b>Groq's Whisper</b> transcription endpoint.
 *
 * <h2>Why Groq (and why the JDK HTTP client)</h2>
 * <p>Groq exposes an OpenAI-compatible transcription API ({@code POST {baseUrl}/audio/transcriptions}),
 * and we already hold a Groq key for the text assistant — so ASR needs <em>no new provider and no new
 * secret</em>. DeepSeek, which we use for vision, has no audio API, which is exactly why the provider
 * for sound is chosen independently (the whole point of the {@link SpeechToText} seam).</p>
 *
 * <p>We call it with the JDK's built-in {@link HttpClient} rather than the Anthropic/OkHttp stack used
 * for vision. That is deliberate: {@code java.net.http} reads the app-server PKCS12 truststore correctly
 * even without a {@code trustStorePassword}, so it sidesteps the GlassFish/Payara TLS pitfall we had to
 * work around for the OkHttp-based vision client. One less moving part.</p>
 *
 * <h2>How the request is shaped</h2>
 * <p>A {@code multipart/form-data} body with three parts: {@code model} (e.g. {@code whisper-large-v3}),
 * {@code response_format=text} (so the reply is the raw transcript, no JSON to parse), and {@code file}
 * (the audio bytes). The audio comes straight from the browser's MediaRecorder, usually WebM/Opus, which
 * Whisper accepts alongside mp3/wav/m4a/ogg/flac.</p>
 *
 * <p><b>Analogy:</b> you slide a voice memo under the window; the clerk types out exactly what was said
 * and slides back the typed note. No interpretation, just transcription.</p>
 */
public final class GroqWhisperSpeechToText implements SpeechToText {

    private static final Logger log = LoggerFactory.getLogger(GroqWhisperSpeechToText.class);

    private final String apiKey;
    private final String endpoint;   // full transcription URL, e.g. https://api.groq.com/openai/v1/audio/transcriptions
    private final String model;

    /** Built once and shared; the JDK client is thread-safe. */
    private volatile HttpClient http;

    /**
     * @param apiKey  the Groq API key (reused from the text provider's config; blank → clear failure on use)
     * @param baseUrl the Groq OpenAI-compatible base URL (e.g. {@code https://api.groq.com/openai/v1/})
     * @param model   the Whisper model id (e.g. {@code whisper-large-v3})
     */
    public GroqWhisperSpeechToText(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.endpoint = join(baseUrl, "audio/transcriptions");
    }

    /** {@inheritDoc} */
    @Override
    public String transcribe(byte[] audio, String mimeType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Groq API key nije podešen — ASR (govor→tekst) poziv preskočen.");
        }
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("Prazan audio — nema šta da se transkribuje.");
        }

        String boundary = "----tabforgevoice" + Long.toHexString(System.nanoTime());
        byte[] body = multipartBody(boundary, audio, mimeType == null ? "audio/webm" : mimeType);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        try {
            log.info("Groq Whisper ASR poziv: model={} bajtova={} mime={}", model, audio.length, mimeType);
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Groq ASR HTTP " + response.statusCode() + ": " + response.body());
            }
            // response_format=text → the body IS the transcript.
            return response.body() == null ? "" : response.body().trim();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Groq ASR poziv nije uspeo: " + ex.getMessage(), ex);
        }
    }

    /** Assemble the multipart/form-data body: model + response_format + the audio file part. */
    private byte[] multipartBody(String boundary, byte[] audio, String mimeType) {
        String dd = "--";
        String crlf = "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // model
            out.write((dd + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"model\"" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            out.write((model + crlf).getBytes(StandardCharsets.UTF_8));
            // response_format=text
            out.write((dd + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"response_format\"" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("text" + crlf).getBytes(StandardCharsets.UTF_8));
            // file
            out.write((dd + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio" + extensionFor(mimeType) + "\"" + crlf)
                    .getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + mimeType + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(audio);
            out.write(crlf.getBytes(StandardCharsets.UTF_8));
            // closing boundary
            out.write((dd + boundary + dd + crlf).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Ne mogu da sastavim multipart telo za ASR: " + ex.getMessage(), ex);
        }
        return out.toByteArray();
    }

    /** A plausible file extension for the upload part's filename (Whisper routes by content, not name). */
    private static String extensionFor(String mimeType) {
        String m = mimeType == null ? "" : mimeType.toLowerCase();
        if (m.contains("webm")) return ".webm";
        if (m.contains("ogg"))  return ".ogg";
        if (m.contains("mp4") || m.contains("m4a")) return ".m4a";
        if (m.contains("mpeg") || m.contains("mp3")) return ".mp3";
        if (m.contains("wav"))  return ".wav";
        return ".webm";
    }

    /** Join a base URL and a path, tolerating a missing/extra trailing slash. */
    private static String join(String base, String path) {
        if (base == null || base.isBlank()) {
            base = "https://api.groq.com/openai/v1/";
        }
        return base.endsWith("/") ? base + path : base + "/" + path;
    }

    private HttpClient client() {
        HttpClient c = http;
        if (c == null) {
            synchronized (this) {
                c = http;
                if (c == null) {
                    c = HttpClient.newHttpClient();
                    http = c;
                }
            }
        }
        return c;
    }
}

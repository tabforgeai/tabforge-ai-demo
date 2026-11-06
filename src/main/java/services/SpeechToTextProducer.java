package services;

import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * The single place that chooses WHICH {@link SpeechToText} (ASR) provider the demo uses — the voice
 * counterpart of {@code VisionProducer}.
 *
 * <h2>What it does</h2>
 * <p>Builds the one app-wide {@link SpeechToText}, reading its key/URL/model from configuration, and
 * publishes it {@code @ApplicationScoped}. Today it returns a {@link GroqWhisperSpeechToText}; to swap
 * providers tomorrow you change this one method.</p>
 *
 * <h2>Config resolution (first non-blank wins)</h2>
 * <p>Each value is resolved: <b>env var → JVM {@code -D} property → {@code easyai.properties}</b>. The
 * Groq key is <em>reused</em> from the text assistant's config, so voice needs no new secret:</p>
 * <ul>
 *   <li>API key: {@code GROQ_API_KEY} → {@code -DGROQ_API_KEY} → {@code easyai.api-key} in easyai.properties.</li>
 *   <li>Base URL: {@code GROQ_BASE_URL} → {@code -DGROQ_BASE_URL} → {@code easyai.base-url}
 *       → default {@code https://api.groq.com/openai/v1/}.</li>
 *   <li>Model: {@code GROQ_WHISPER_MODEL} → {@code -DGROQ_WHISPER_MODEL} → default {@code whisper-large-v3}.</li>
 * </ul>
 *
 * <p><b>Analogy:</b> the same fuse box as vision, wired to a different appliance. Power enters at one
 * controlled spot; the rest of the flat never looks inside the wall to learn which utility it came from.</p>
 */
@ApplicationScoped
public class SpeechToTextProducer {

    private static final Logger log = LoggerFactory.getLogger(SpeechToTextProducer.class);

    /** Lazily-loaded view of {@code easyai.properties} (the lowest-priority config source). */
    private static volatile Properties easyAiProps;

    /**
     * Build the app-wide {@link SpeechToText}. Swap the returned implementation to change ASR provider.
     *
     * @return a ready, provider-agnostic speech-to-text reader (currently Groq Whisper)
     */
    @Produces
    @ApplicationScoped
    public SpeechToText speechToText() {
        String apiKey = resolve("GROQ_API_KEY", "easyai.api-key", "");
        String baseUrl = resolve("GROQ_BASE_URL", "easyai.base-url", "https://api.groq.com/openai/v1/");
        String model = resolve("GROQ_WHISPER_MODEL", null, "whisper-large-v3");

        if (apiKey.isBlank()) {
            log.warn("Groq API key nije pronađen (GROQ_API_KEY / -D / easyai.api-key) — glas (ASR) će pasti dok ga ne podesiš.");
        } else {
            log.info("ASR provajder spreman: model={} baseUrl={}", model, baseUrl);
        }
        return new GroqWhisperSpeechToText(apiKey, baseUrl, model);
    }

    /**
     * Resolve a value by precedence: env var {@code envName}, then JVM {@code -DenvName}, then
     * {@code easyai.properties} under {@code propName} (skipped if {@code null}); else {@code defaultValue}.
     */
    private static String resolve(String envName, String propName, String defaultValue) {
        String v = System.getenv(envName);
        if (isBlank(v)) {
            v = System.getProperty(envName);
        }
        if (isBlank(v) && propName != null) {
            v = properties().getProperty(propName);
        }
        return isBlank(v) ? defaultValue : v.trim();
    }

    /** Load {@code easyai.properties} once from the classpath; an empty {@link Properties} if missing. */
    private static Properties properties() {
        Properties p = easyAiProps;
        if (p == null) {
            synchronized (SpeechToTextProducer.class) {
                p = easyAiProps;
                if (p == null) {
                    p = new Properties();
                    try (InputStream in = Thread.currentThread().getContextClassLoader()
                            .getResourceAsStream("easyai.properties")) {
                        if (in != null) {
                            p.load(in);
                        }
                    } catch (Exception ex) {
                        log.warn("Ne mogu da pročitam easyai.properties: {}", ex.getMessage());
                    }
                    easyAiProps = p;
                }
            }
        }
        return p;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

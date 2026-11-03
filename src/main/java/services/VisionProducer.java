package services;

import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * <h2>VisionProducer — the single place that chooses WHICH vision provider the demo uses</h2>
 *
 * <p><b>What it does:</b> builds the one {@link VisionEkstraktor} the whole app injects, reading the
 * provider's key/URL/model from configuration. Today it hands back a {@link DeepSeekVision}; to switch
 * providers tomorrow you change this one method — nothing else in the demo knows or cares.</p>
 *
 * <h3>Why a CDI producer (and not {@code @Inject} on DeepSeekVision directly)</h3>
 * <p>{@link DeepSeekVision} is a plain object (it takes three strings), which keeps it easy to unit
 * test and free of framework glue. This producer is the thin CDI adapter that feeds it the config
 * values and publishes it {@code @ApplicationScoped} (built once, shared). It is the "wiring panel":
 * the seam where a concrete provider is selected lives here, on purpose, alone.</p>
 *
 * <h3>Where the config comes from — one resolver, three places (first wins)</h3>
 * <p>So you can keep secrets wherever is natural without editing code, each value is looked up in this
 * order and the first non-blank hit wins:</p>
 * <ol>
 *   <li><b>OS / server environment variable</b> (e.g. {@code DEEPSEEK_API_KEY}) — the cleanest for
 *       production: the secret lives only in the Payara process environment, never in a file inside the WAR.</li>
 *   <li><b>JVM system property</b> (e.g. {@code -DDEEPSEEK_API_KEY=...}, i.e. Payara
 *       {@code asadmin create-jvm-options}) — handy when you prefer to keep it in the server's own config.</li>
 *   <li><b>{@code easyai.properties} on the classpath</b> — the demo's "everything in one place" option:
 *       the same file that already holds the EasyAI (Groq) key can carry {@code DEEPSEEK_API_KEY=...} too.</li>
 * </ol>
 *
 * <p><b>Security:</b> options 2 and 3 put the secret in a file; {@code easyai.properties} must be
 * stripped before any public commit (it already carries the Groq key — same rule applies to the
 * DeepSeek key). Option 1 avoids that entirely. The precedence lets production override the file
 * with a real env var without rebuilding.</p>
 *
 * <h3>Keys</h3>
 * <ul>
 *   <li>{@code DEEPSEEK_API_KEY} — the DeepSeek key (may be absent; the call then fails with a clear
 *       message instead of crashing the boot).</li>
 *   <li>{@code DEEPSEEK_BASE_URL} — default {@code https://api.deepseek.com/anthropic}.</li>
 *   <li>{@code DEEPSEEK_VISION_MODEL} — default {@code deepseek-flash}.</li>
 * </ul>
 *
 * <p><b>Analogy:</b> the fuse box. Power (the secret key) can enter from the mains (env var), a
 * backup generator (JVM property), or a battery kept in the flat ({@code easyai.properties}); this
 * box picks whichever is live, in that order, and feeds the rest of the house. Want to know which
 * provider the app talks to? You look here, not in the walls.</p>
 */
@ApplicationScoped
public class VisionProducer {

    private static final Logger log = LoggerFactory.getLogger(VisionProducer.class);

    /** Lazily-loaded view of {@code easyai.properties} (the 3rd, lowest-priority config source). */
    private static volatile Properties easyAiProps;

    /**
     * Build the app-wide {@link VisionEkstraktor}. Swap the returned implementation to change provider.
     *
     * @return a ready, provider-agnostic image reader (currently DeepSeek-backed)
     */
    @Produces
    @ApplicationScoped
    public VisionEkstraktor visionEkstraktor() {
        String apiKey = resolve("DEEPSEEK_API_KEY", "");
        String baseUrl = resolve("DEEPSEEK_BASE_URL", "https://api.deepseek.com/anthropic");
        String model = resolve("DEEPSEEK_VISION_MODEL", "deepseek-flash");

        if (apiKey.isBlank()) {
            log.warn("DEEPSEEK_API_KEY nije pronadjen (env / -D / easyai.properties) — vision poziv ce pasti dok ga ne podesis.");
        } else {
            log.info("Vision provajder spreman: model={} baseUrl={}", model, baseUrl);
        }
        return new DeepSeekVision(apiKey, baseUrl, model);
    }

    /**
     * Resolve one config value by precedence: env var, then JVM {@code -D} property, then
     * {@code easyai.properties}; falls back to {@code defaultno} when none is set or all are blank.
     *
     * @param ime       the key name, identical across all three sources (e.g. {@code "DEEPSEEK_API_KEY"})
     * @param defaultno the value to use when the key is absent/blank everywhere
     * @return the first non-blank value found, or {@code defaultno}
     */
    private static String resolve(String ime, String defaultno) {
        String v = System.getenv(ime);                       // 1) OS / server environment
        if (isBlank(v)) {
            v = System.getProperty(ime);                     // 2) JVM -D system property
        }
        if (isBlank(v)) {
            v = properties().getProperty(ime);               // 3) easyai.properties on the classpath
        }
        return isBlank(v) ? defaultno : v.trim();
    }

    /** Load {@code easyai.properties} once from the classpath; an empty {@link Properties} if it is missing. */
    private static Properties properties() {
        Properties p = easyAiProps;
        if (p == null) {
            synchronized (VisionProducer.class) {
                p = easyAiProps;
                if (p == null) {
                    p = new Properties();
                    try (InputStream in = Thread.currentThread().getContextClassLoader()
                            .getResourceAsStream("easyai.properties")) {
                        if (in != null) {
                            p.load(in);
                        } else {
                            log.debug("easyai.properties nije na classpath-u — preskacem taj izvor konfiguracije.");
                        }
                    } catch (Exception ex) {
                        log.warn("Ne mogu da procitam easyai.properties: {}", ex.getMessage());
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

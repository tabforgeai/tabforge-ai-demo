package services;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Base64;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;

/**
 * <h2>DeepSeekVision — the {@link VisionEkstraktor} backed by DeepSeek's vision model</h2>
 *
 * <p><b>What it does:</b> sends one image to DeepSeek and returns the text it reads back. The trick
 * (borrowed from the {@code marketplace-intel} project's {@code DeepSeekKlijent}) is that DeepSeek
 * exposes an <b>Anthropic-compatible</b> endpoint ({@code https://api.deepseek.com/anthropic}), so
 * we drive it with the official {@code com.anthropic:anthropic-java} SDK — the very same SDK you
 * would use for Claude. We only point its {@code baseUrl} at DeepSeek and give it a DeepSeek key.
 * No new SDK, no hand-rolled HTTP.</p>
 *
 * <h3>Who does what — extraction vs. the final answer (two models, one flow)</h3>
 * <p>It is easy to assume "DeepSeek answered me" — it did not. The user-facing reply is written by a
 * <em>different</em> model. The split is deliberate:</p>
 * <ul>
 *   <li><b>DeepSeek = the eyes (STEP 1).</b> This class turns the image into cold facts (the extraction
 *       JSON). It <b>never writes prose to the user</b> — it only reads pixels into text.</li>
 *   <li><b>The EasyAI assistant = the voice (STEP 2).</b> The reply ("the invoice is €240 higher than
 *       the order — update it?") is produced by the tab-aware assistant in
 *       {@code cdibeans.GlobalAiAssistantBean}, which is the {@code EasyAI.assistant(...)} configured in
 *       {@code easyai.properties} — in this demo that is <b>Groq</b> ({@code openai/gpt-oss-120b}). It
 *       reasons over DeepSeek's facts plus the live order read through the {@code @EasyTool} tools.</li>
 * </ul>
 * <p><b>Where the "who does what" is decided:</b></p>
 * <ol>
 *   <li><b>Which endpoint</b> (and therefore whether DeepSeek is involved at all) is chosen in the
 *       browser: {@code template.xhtml}'s {@code InputEventBus.on('user_message')} routes a message
 *       <em>with an image</em> to {@code POST /api/ai/vision} and a <em>text-only</em> message to
 *       {@code POST /api/ai/chat}.</li>
 *   <li><b>The vision provider</b> is fixed in exactly one place — {@code services.VisionProducer}
 *       (the CDI producer) returns this {@code DeepSeekVision} as the {@link VisionEkstraktor}. Swap it
 *       there to change who "sees".</li>
 *   <li><b>The answering provider</b> is the EasyAI assistant (Groq), chosen once in
 *       {@code easyai.properties}; it is the same voice whether the message arrived with an image or
 *       as plain text. So "Where am I?" (text) and "compare this invoice" (image) are both answered by
 *       Groq — only the image case runs DeepSeek first.</li>
 * </ol>
 *
 * <h3>How an image is sent (base64 inline)</h3>
 * <p>The Anthropic message format carries a picture as an <em>image content block</em> inside a
 * <b>user</b> message: {@code source.type="base64"}, a {@code media_type}, and the bytes as a
 * base64 string. Images are allowed only in user messages (system/assistant → HTTP 400). We attach
 * exactly two blocks: the text instruction, then the image.</p>
 *
 * <h3>Lazy client (boot even without a key)</h3>
 * <p>The SDK client is built on first use (double-checked locking), not in the constructor — so the
 * demo WAR starts cleanly even when {@code DEEPSEEK_API_KEY} is absent. The failure (missing key)
 * surfaces only when someone actually sends a picture, and the REST layer turns it into a readable
 * sentence instead of a 500.</p>
 *
 * <p><b>Analogy:</b> the same TV remote (Anthropic SDK) pointed at a different set (DeepSeek). The
 * buttons are identical; you are just aiming at another box. "Show me what's on this paper" is one
 * of those buttons — it happens to be the camera input.</p>
 *
 * <p><b>Model:</b> the vision model id is {@code deepseek-flash} (NOT {@code deepseek-v4-flash},
 * which is DeepSeek's text measure model). The retired alias {@code deepseek-v4-flash-vision-exp}
 * still routes here but should not be used.</p>
 *
 * <h3>TLS on an app server (why we hand the client its own trust manager)</h3>
 * <p>On a plain JDK the HTTPS call "just works" because the client reads the JDK's {@code cacerts}
 * (full of public CA roots). On GlassFish/Payara the JVM is told to use the server's own truststore
 * ({@code config/cacerts.p12}) — and OkHttp (the HTTP engine inside the Anthropic SDK) fails to open
 * that PKCS12 store because no {@code trustStorePassword} is supplied, so it ends up with <em>zero</em>
 * trust anchors and the handshake dies with {@code "the trustAnchors parameter must be non-empty"}.
 * Rather than depend on server JVM flags, we load the truststore ourselves — correctly, with its
 * password and type — and pass the resulting {@link X509TrustManager} straight to the client via
 * {@link AnthropicOkHttpClient.Builder#trustManager}. The demo is then self-sufficient: no
 * {@code domain.xml} edits needed. (Note {@code java.net.http}, which the EasyAI/Groq path uses,
 * reads the same store correctly even without a password — which is why Groq worked but this did not.)</p>
 */
public final class DeepSeekVision implements VisionEkstraktor {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekVision.class);

    /** Upper bound on tokens in the reply. Extraction is short (an iznos + a few stavke), so 1024 is plenty. */
    private static final long MAX_TOKENA = 1024L;

    private final String apiKey;
    private final String baseUrl;
    private final String model;

    /** Built lazily and shared; {@code volatile} because a REST request thread may create it "at the same time". */
    private volatile AnthropicClient client;

    /**
     * @param apiKey  DeepSeek API key (from env; may be blank — then {@link #procitaj} fails with a clear message)
     * @param baseUrl DeepSeek's Anthropic-compatible base URL (e.g. {@code https://api.deepseek.com/anthropic})
     * @param model   the vision model id (e.g. {@code deepseek-flash})
     */
    public DeepSeekVision(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    /** {@inheritDoc} */
    @Override
    public String procitaj(byte[] slika, String mimeType, String uputstvo) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DEEPSEEK_API_KEY nije podesen — vision poziv preskocen.");
        }
        if (slika == null || slika.length == 0) {
            throw new IllegalArgumentException("Prazna slika — nema sta da se procita.");
        }

        String b64 = Base64.getEncoder().encodeToString(slika);

        // One image block (base64 source) + the text instruction, both in a single USER message.
        ImageBlockParam slikaBlok = ImageBlockParam.builder()
                .source(Base64ImageSource.builder()
                        .mediaType(Base64ImageSource.MediaType.of(normalizujMime(mimeType)))
                        .data(b64)
                        .build())
                .build();

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENA)
                .addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofText(uputstvo),
                        ContentBlockParam.ofImage(slikaBlok)))
                .build();

        log.info("DeepSeek vision poziv: model={} mime={} bajtova={}", model, mimeType, slika.length);
        Message odgovor = klijent().messages().create(params);
        return izvuciTekst(odgovor);
    }

    /**
     * DeepSeek accepts only jpeg/png/gif/webp. A browser MIME may carry parameters
     * (e.g. {@code "image/jpeg; charset=..."}); keep just the bare type, default to jpeg if unknown.
     */
    private static String normalizujMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return "image/jpeg";
        }
        String bare = mime.split(";")[0].trim().toLowerCase();
        return switch (bare) {
            case "image/jpeg", "image/png", "image/gif", "image/webp" -> bare;
            default -> "image/jpeg";
        };
    }

    /** Build the SDK client lazily, pointed at DeepSeek's Anthropic-compatible endpoint. */
    private AnthropicClient klijent() {
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    // Read the truststore ourselves (see class doc) and derive BOTH pieces the SDK
                    // insists on together: the trust manager AND a matching SSL socket factory.
                    X509TrustManager tm = trustManager();
                    c = AnthropicOkHttpClient.builder()
                            .apiKey(apiKey)
                            .baseUrl(baseUrl)          // the only real difference from a Claude client
                            .sslSocketFactory(sslSocketFactory(tm))
                            .trustManager(tm)
                            .build();
                    client = c;
                }
            }
        }
        return c;
    }

    /**
     * Build an {@link X509TrustManager} from the JVM's truststore, read the <em>correct</em> way.
     *
     * <p>Precedence mirrors the JSSE defaults, but we always supply a password (default
     * {@code "changeit"}) so a PKCS12 store opens cleanly — the exact step OkHttp skips:</p>
     * <ol>
     *   <li>{@code -Djavax.net.ssl.trustStore} if the server set one (GlassFish → {@code config/cacerts.p12},
     *       which now also holds the public CA roots), with its {@code trustStoreType}/{@code trustStorePassword};</li>
     *   <li>otherwise the JDK's own {@code ${java.home}/lib/security/cacerts} (PKCS12, {@code changeit}).</li>
     * </ol>
     *
     * <p><b>Analogy:</b> the SDK came with a key to the building's mailroom but kept fumbling the lock.
     * We open the same mailroom with the key that actually turns, hand it the stack of trusted
     * letterheads (CA roots), and let it get on with the delivery.</p>
     *
     * @return the first {@link X509TrustManager} the truststore yields
     */
    private static X509TrustManager trustManager() {
        try {
            String path = System.getProperty("javax.net.ssl.trustStore");
            String type = System.getProperty("javax.net.ssl.trustStoreType", KeyStore.getDefaultType());
            String pass = System.getProperty("javax.net.ssl.trustStorePassword", "changeit");

            if (path == null || path.isBlank()) {
                path = System.getProperty("java.home") + "/lib/security/cacerts";
                type = "PKCS12";
            }

            KeyStore ks = KeyStore.getInstance(type);
            try (InputStream in = Files.newInputStream(Path.of(path))) {
                ks.load(in, pass.toCharArray());
            }
            log.info("Truststore ucitan za vision TLS: {} (tip={}, unosa={})", path, type, ks.size());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager x) {
                    return x;
                }
            }
            throw new IllegalStateException("Nije pronadjen X509TrustManager u truststore-u: " + path);
        } catch (Exception ex) {
            throw new IllegalStateException("Ne mogu da pripremim TLS truststore za vision poziv: " + ex.getMessage(), ex);
        }
    }

    /**
     * Build an {@link javax.net.ssl.SSLSocketFactory} that trusts exactly what {@code tm} trusts.
     *
     * <p>OkHttp (and therefore the Anthropic SDK) requires the socket factory and the trust manager
     * to agree, so we derive the factory from the very same {@link X509TrustManager}. Default key
     * managers (first arg {@code null}) are fine — we present no client certificate, we only verify
     * the server. <b>Analogy:</b> same guest list (the trust manager), same doorman reading it (the
     * socket factory); the SDK won't let the two be strangers.</p>
     *
     * @param tm the trust manager the client will also be given
     * @return a TLS socket factory backed by {@code tm}
     */
    private static javax.net.ssl.SSLSocketFactory sslSocketFactory(X509TrustManager tm) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] { tm }, null);
            return ctx.getSocketFactory();
        } catch (Exception ex) {
            throw new IllegalStateException("Ne mogu da napravim SSLSocketFactory za vision poziv: " + ex.getMessage(), ex);
        }
    }

    /** Join all text blocks of the reply into one string (same shape as DeepSeekKlijent — same SDK). */
    private static String izvuciTekst(Message odgovor) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock blok : odgovor.content()) {
            blok.text().ifPresent(t -> {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(t.text());
            });
        }
        return sb.toString();
    }
}

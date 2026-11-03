package api;

import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cdibeans.GlobalAiAssistantBean;
import dyntabs.ai.EasyAI;
import jakarta.inject.Inject;
import services.SuggestionAdvisor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import services.VisionEkstraktor;

/**
 * The {@code POST /api/ai/vision} endpoint — the demo's "wow": the assistant <em>sees</em> a picture.
 *
 * <h2>Why this endpoint exists</h2>
 * <p>The template's chat input already emits a {@code user_message} event with any attached files.
 * When that message carries an image, the frontend routes it here (as {@code multipart/form-data})
 * instead of to {@link AiChatResource}. This resource runs the two-step "see → think" flow and
 * returns the assistant's answer as plain text, exactly like the chat endpoint, so the panel renders
 * it the same way ({@code PFTemplate.aiPanel.streamAssistant(reply)}).</p>
 *
 * <h2>The two steps (why vision is kept SEPARATE from the assistant)</h2>
 * <pre>
 *   STEP 1  SEE   : {@link VisionEkstraktor} reads the image → cold facts (JSON). Cheap, fast, provider-agnostic.
 *   STEP 2  THINK : {@link GlobalAiAssistantBean#ask(String, String)} takes those facts + the user's words →
 *                   reasons with the SAME tab-aware tools (compare with the purchase order open in the
 *                   other tab, offer to file a claim). The library's model never touches the image —
 *                   only the extracted text does.
 * </pre>
 *
 * <h2>Companions</h2>
 * <p>{@link AiChatResource} ({@code POST /chat}) is the text twin; {@link AiStreamResource}
 * ({@code GET /stream}) is the outbound live feed that later carries the proactive "they don't
 * match — file a claim?" chip. This resource is the inbound <em>image</em> request.</p>
 *
 * <h2>Familiar analogy</h2>
 * <p>You slide a paper under the drive-through window (the multipart upload). A clerk reads the key
 * numbers off it (STEP 1), then the manager decides what to do with those numbers (STEP 2) and
 * answers you through the same window.</p>
 */
@Path("/ai")
public class AiVisionResource {

    private static final Logger log = LoggerFactory.getLogger(AiVisionResource.class);

    /** The instruction handed to the vision model. Owned here (demo-specific) so the extractor stays generic. */
    private static final String UPUTSTVO =
            "Pročitaj priloženi dokument (faktura/nalog/formular). Vrati SAMO JSON, bez objašnjenja: "
          + "{\"iznos\": broj, \"valuta\": \"...\", \"stavke\": [{\"naziv\": \"...\", \"iznos\": broj}]}. "
          + "Ako neki podatak ne postoji, izostavi ga.";

    /** Matches the {@code "iznos": <number>} field in the extraction JSON (optionally quoted, with spaces). */
    private static final Pattern IZNOS = Pattern.compile("\"iznos\"\\s*:\\s*\"?([-0-9.,\\s]+)");

    @Inject
    private GlobalAiAssistantBean globalAssistant;

    @Inject
    private VisionEkstraktor vision;

    /** The proactive engine — we hand it the invoice amount so it can offer a claim if it mismatches the order. */
    @Inject
    private SuggestionAdvisor suggestionAdvisor;

    /**
     * Handle one image (plus optional text) from the AI panel.
     *
     * <p>Reads the multipart parts the portable Jakarta REST way ({@link EntityPart}): a {@code file}
     * part (the image bytes + its media type) and an optional {@code text} part (whatever the user
     * typed alongside the picture). Then runs STEP 1 (see) → STEP 2 (think) and returns the reply.
     * Any failure becomes a readable sentence rather than an HTTP 500, so the panel always shows
     * something useful.</p>
     *
     * @param request the HTTP request, used only for the session id that scopes the assistant to this user
     * @param parts   the multipart form parts; we look for {@code "file"} (required) and {@code "text"} (optional)
     * @return the assistant's reply as plain text
     */
    @POST
    @Path("/vision")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_PLAIN)
    public String vision(@Context HttpServletRequest request, List<EntityPart> parts) {
        String sessionId = request.getSession(true).getId();
        try {
            byte[] slika = null;
            String mime = "image/jpeg";
            String tekst = "";

            for (EntityPart p : parts) {
                switch (p.getName()) {
                    case "file" -> {
                        slika = p.getContent().readAllBytes();
                        mime = p.getMediaType() != null ? p.getMediaType().toString() : mime;
                    }
                    case "text" -> tekst = p.getContent(String.class);
                    default -> { /* ignore unknown parts */ }
                }
            }

            if (slika == null || slika.length == 0) {
                return "Nisam dobio sliku — probaj ponovo da priložiš dokument.";
            }

            // STEP 1 — SEE: image → cold facts (JSON). Provider-agnostic; DeepSeek does not touch our tabs.
            String izvuceno = vision.procitaj(slika, mime, UPUTSTVO);
            log.info("Vision izvukao {} karaktera iz slike ({} bajtova).", izvuceno.length(), slika.length);

            // STEP 2 — THINK + ACT: hand the facts (+ the user's words) to the SAME tab-aware assistant.
            String prompt = (tekst == null || tekst.isBlank() ? "" : tekst.trim() + "\n\n")
                    + "[Podaci izvučeni sa priložene slike]\n" + izvuceno;
            String odgovor = globalAssistant.ask(sessionId, prompt);

            // STEP 3 — PROACTIVE (fire-and-forget): if the invoice amount mismatches the order the user
            // is looking at, the advisor pushes a deterministic Accept/Dismiss "file a claim?" chip over
            // SSE. The decision is arithmetic, not the LLM's; see SuggestionAdvisor.onInvoiceExtracted.
            OptionalDouble iznos = parseIznos(izvuceno);
            if (iznos.isPresent()) {
                suggestionAdvisor.onInvoiceExtracted(sessionId, iznos.getAsDouble());
            } else {
                log.debug("Nije pronađen 'iznos' u ekstrakciji — preskačem proaktivnu proveru.");
            }

            return odgovor;
        } catch (Exception ex) {
            log.error("AiVisionResource.vision() failed: {}", ex.getMessage(), ex);
            return "Žao mi je — nisam uspeo da obradim sliku: " + EasyAI.extractErrorMessage(ex);
        }
    }

    /**
     * Pull the numeric {@code iznos} out of the vision extraction JSON, tolerant of how a model may format
     * a number: optional quotes, thousands spaces, and either {@code ,} or {@code .} as the decimal mark.
     *
     * <p>The instruction asks for a plain JSON number, but models are not perfectly consistent, so we
     * normalise defensively and simply skip the proactive check if nothing parses — a missing amount must
     * never turn into an error on the user's reply.</p>
     *
     * @param json the STEP 1 extraction text (e.g. {@code {"iznos": 2990.00, "valuta": "EUR", ...}})
     * @return the parsed amount, or empty if no {@code iznos} could be read
     */
    private static OptionalDouble parseIznos(String json) {
        if (json == null) {
            return OptionalDouble.empty();
        }
        Matcher m = IZNOS.matcher(json);
        if (!m.find()) {
            return OptionalDouble.empty();
        }
        String t = m.group(1).replaceAll("\\s", "");
        // Decide which separator is the decimal mark, then drop the thousands separator.
        boolean commaIsDecimal = t.contains(",") && (!t.contains(".") || t.lastIndexOf(',') > t.lastIndexOf('.'));
        t = commaIsDecimal ? t.replace(".", "").replace(",", ".") : t.replace(",", "");
        try {
            return OptionalDouble.of(Double.parseDouble(t));
        } catch (NumberFormatException ex) {
            return OptionalDouble.empty();
        }
    }
}

package api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cdibeans.GlobalAiAssistantBean;
import dyntabs.ai.EasyAI;
import dyntabs.ai.voice.Speech;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * The <b>voice</b> channel of the demo: {@code POST /api/ai/voice} (speech in) and
 * {@code POST /api/ai/speak} (speech out).
 *
 * <h2>What this class is really demonstrating</h2>
 * <p>Both directions are one line of EasyAI. This demo used to carry its own speech-to-text
 * implementation — a provider interface, a hand-written multipart HTTP client and a CDI producer,
 * roughly 290 lines across three classes. All of it is gone; the library does it now:</p>
 * <pre>
 *   speech to text : EasyAI.listen().from(audio, mimeType)   returns String
 *   text to speech : EasyAI.speak().from(text)               returns Speech (bytes + MIME type)
 * </pre>
 * <p>That deletion <em>is</em> the demonstration. Nothing here knows which provider answers, which
 * endpoint is called, or how the audio is encoded — those live in {@code easyai.properties}, and
 * each capability may point at a different provider.</p>
 *
 * <h2>The cascade (speech in)</h2>
 * <pre>
 *   STEP 1  HEAR  : EasyAI.listen() turns the recorded audio into text.
 *   STEP 2  THINK : GlobalAiAssistantBean.ask hands that text to the SAME tab-aware assistant,
 *                   so a spoken "cancel this order" behaves exactly like the typed one — tools,
 *                   tabs, ambient memory and the proactive layer all apply unchanged.
 * </pre>
 * <p>Voice is a new <em>channel</em>, not a new brain: the assistant never learns that the words
 * arrived as sound. This mirrors the vision endpoint ({@code /api/ai/vision}) — a thin "sense" step
 * in front of the one assistant.</p>
 *
 * <h2>Why speech-in returns JSON</h2>
 * <p>Unlike a typed turn, the user never saw their own words, so the browser needs the transcript
 * back to render the "you said …" bubble, plus the answer. Hence a small JSON object rather than the
 * plain text the chat and vision endpoints return.</p>
 *
 * <h2>Why a server-side speak endpoint at all</h2>
 * <p>The browser can already speak text for free through the Web Speech API, and that stays the
 * default here. Synthesizing on the server earns its keep when you need one consistent voice across
 * browsers and devices, a language the client has no installed voice for, or audio you can store and
 * replay. This endpoint demonstrates that path.</p>
 *
 * <p><b>Analogy:</b> a receptionist who takes your spoken message, writes it down (STEP 1), passes
 * the note to the manager who decides what to do (STEP 2), and reads the reply back to you.</p>
 */
@Path("/ai")
public class AiVoiceResource {

    private static final Logger log = LoggerFactory.getLogger(AiVoiceResource.class);

    @Inject
    private GlobalAiAssistantBean globalAssistant;

    /**
     * Handle one recorded utterance: transcribe it, then let the tab-aware assistant answer.
     *
     * @param request the HTTP request, used only for the session id that scopes the assistant
     * @param parts   the multipart form parts; we look for an {@code "audio"} part (the recording)
     * @return a {@link VoiceReply} with the transcript and the assistant's answer
     */
    @POST
    @Path("/voice")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public VoiceReply voice(@Context HttpServletRequest request, List<EntityPart> parts) {
        String sessionId = request.getSession(true).getId();
        try {
            byte[] audio = null;
            String mime = "audio/webm";
            for (EntityPart p : parts) {
                if ("audio".equals(p.getName())) {
                    audio = p.getContent().readAllBytes();
                    mime = p.getMediaType() != null ? p.getMediaType().toString() : mime;
                }
            }
            if (audio == null || audio.length == 0) {
                return new VoiceReply("", "Nisam dobio zvuk — probaj ponovo da snimiš poruku.");
            }

            // STEP 1 — HEAR. One line; provider, model and key come from easyai.properties.
            String transcript = EasyAI.listen().from(audio, mime);
            log.info("Voice transkript ({} bajtova audia): \"{}\"", audio.length, transcript);
            if (transcript == null || transcript.isBlank()) {
                return new VoiceReply("", "Nisam razumeo šta je rečeno — probaj ponovo.");
            }

            // STEP 2 — THINK: the SAME tab-aware assistant answers the transcribed words.
            String answer = globalAssistant.ask(sessionId, transcript);
            return new VoiceReply(transcript, answer);
        } catch (Exception ex) {
            log.error("AiVoiceResource.voice() failed: {}", ex.getMessage(), ex);
            return new VoiceReply("", "Žao mi je — nisam uspeo da obradim glasovnu poruku: "
                    + EasyAI.extractErrorMessage(ex));
        }
    }

    /**
     * Speak a piece of text on the server and stream the audio back.
     *
     * <p>{@link Speech} carries the bytes <em>and</em> the MIME type on purpose: a bare
     * {@code byte[]} is not enough to answer an HTTP request, and guessing the content type is how
     * audio ends up silently unplayable in a browser.</p>
     *
     * @param text the words to speak (the raw request body)
     * @return the audio with its content type, or a readable error
     */
    @POST
    @Path("/speak")
    @Consumes(MediaType.TEXT_PLAIN)
    public Response speak(String text) {
        if (text == null || text.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN).entity("Nema teksta za izgovaranje.").build();
        }
        try {
            Speech speech = EasyAI.speak().from(text);
            log.info("TTS: {} znakova -> {}", text.length(), speech);
            return Response.ok(speech.bytes()).type(speech.mimeType()).build();
        } catch (Exception ex) {
            log.error("AiVoiceResource.speak() failed: {}", ex.getMessage(), ex);
            return Response.serverError().type(MediaType.TEXT_PLAIN)
                    .entity("Sinteza govora nije uspela: " + EasyAI.extractErrorMessage(ex)).build();
        }
    }

    /** Minimal JSON reply for the inbound direction: the recognised words and the answer. */
    public static class VoiceReply {

        private String transcript;
        private String answer;

        public VoiceReply() {
        }

        public VoiceReply(String transcript, String answer) {
            this.transcript = transcript;
            this.answer = answer;
        }

        public String getTranscript() {
            return transcript;
        }

        public void setTranscript(String transcript) {
            this.transcript = transcript;
        }

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }
    }
}

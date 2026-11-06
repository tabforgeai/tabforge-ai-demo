package api;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cdibeans.GlobalAiAssistantBean;
import dyntabs.ai.EasyAI;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import services.SpeechToText;

/**
 * The {@code POST /api/ai/voice} endpoint — the inbound <b>voice</b> channel (speech → answer).
 *
 * <h2>What it does (the cascade)</h2>
 * <pre>
 *   STEP 1  HEAR  : {@link SpeechToText#transcribe} turns the recorded audio into text (Groq Whisper).
 *   STEP 2  THINK : {@link GlobalAiAssistantBean#ask} hands that text to the SAME tab-aware assistant,
 *                   so a spoken "cancel this order" works exactly like the typed one — tools, tabs,
 *                   ambient memory and the proactive layer all apply unchanged.
 * </pre>
 * <p>Voice is a new <em>channel</em>, not a new brain: the library's assistant never knows the words
 * arrived as sound. This mirrors the vision endpoint ({@code /api/ai/vision}): a thin "sense" step in
 * front of the one assistant.</p>
 *
 * <h2>Why it returns JSON (transcript + answer)</h2>
 * <p>Unlike a typed turn, the user never saw their own words, so the browser needs the transcript back to
 * render it as the "you said …" bubble, plus the assistant's answer. Hence a small JSON object rather
 * than the plain text the chat/vision endpoints return.</p>
 *
 * <h2>Scope note (demo-only, library-ready)</h2>
 * <p>This resource and the browser capture are application wiring and stay in the app. The reusable part
 * — the {@link SpeechToText} capability — lives behind a clean seam so it can later be promoted into the
 * EasyAI library without touching this endpoint.</p>
 *
 * <p><b>Analogy:</b> a receptionist who takes your spoken message, writes it down (STEP 1), passes the
 * note to the manager who decides what to do (STEP 2), and reads both the note and the reply back to you.</p>
 */
@Path("/ai")
public class AiVoiceResource {

    private static final Logger log = LoggerFactory.getLogger(AiVoiceResource.class);

    @Inject
    private SpeechToText speechToText;

    @Inject
    private GlobalAiAssistantBean globalAssistant;

    /**
     * Handle one recorded utterance: transcribe it, then let the tab-aware assistant answer.
     *
     * @param request the HTTP request, used only for the session id that scopes the assistant to this user
     * @param parts   the multipart form parts; we look for an {@code "audio"} part (the recorded bytes)
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

            // STEP 1 — HEAR: audio → text.
            String transcript = speechToText.transcribe(audio, mime);
            log.info("Voice transkript ({} bajtova audia): \"{}\"", audio.length, transcript);
            if (transcript == null || transcript.isBlank()) {
                return new VoiceReply("", "Nisam razumeo šta je rečeno — probaj ponovo.");
            }

            // STEP 2 — THINK: the SAME tab-aware assistant answers the transcribed words.
            String answer = globalAssistant.ask(sessionId, transcript);
            return new VoiceReply(transcript, answer);
        } catch (Exception ex) {
            log.error("AiVoiceResource.voice() failed: {}", ex.getMessage(), ex);
            return new VoiceReply("", "Žao mi je — nisam uspeo da obradim glasovnu poruku: " + EasyAI.extractErrorMessage(ex));
        }
    }

    /** Minimal JSON reply: the recognised words and the assistant's answer. */
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

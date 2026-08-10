package api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import services.SuggestionAdvisor;

/**
 * The small {@code POST /api/ai/suggestion} endpoint that closes the loop on a proactive suggestion:
 * it records the user's <b>accept</b> or <b>dismiss</b> so the {@link SuggestionAdvisor} can manage its
 * cooldowns.
 *
 * <h2>Why accepting needs almost nothing here</h2>
 * <p>The <em>work</em> of an accepted suggestion does not happen in this resource. When the user clicks
 * "accept" on the chip, the browser simply injects the suggestion's ready-made prompt into the ordinary
 * chat input and sends it through {@code POST /api/ai/chat} — reusing the existing tab-aware assistant and
 * its tools. This endpoint's only job for an accept is bookkeeping: restart the cooldown so the same
 * suggestion does not pop again a moment later. A dismiss is the same call with the opposite intent —
 * mute this suggestion for a good while.</p>
 *
 * <h2>Familiar analogy</h2>
 * <p>A "not interested" / "sounds good" card you drop in a box on your way out. It does not perform the
 * offer — it just tells the host whether to bring it up again.</p>
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   chip button → fetch POST /api/ai/suggestion {key, decision}
 *        → this resource → SuggestionAdvisor.accepted(key) | dismissed(key)
 *   (accept ALSO, separately: chip injects prompt → POST /api/ai/chat → the assistant answers)
 * </pre>
 */
@Path("/ai")
public class SuggestionResource {

    private static final Logger log = LoggerFactory.getLogger(SuggestionResource.class);

    @Inject
    private SuggestionAdvisor advisor;

    /**
     * Record the user's decision on a suggestion.
     *
     * <p>The session id is read from the request (as in {@code AiChatResource}) so the advisor can scope
     * suppression per user — the same id used when the suggestion was pushed.</p>
     *
     * @param request the HTTP request, used only to obtain the session id
     * @param body    the JSON payload {@code {"key": "...", "decision": "accept"|"dismiss"}}
     * @return {@code 204 No Content} (the response body is irrelevant to the chip)
     */
    @POST
    @Path("/suggestion")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response decide(@Context HttpServletRequest request, SuggestionDecision body) {
        if (body == null || body.getKey() == null || body.getKey().isBlank()) {
            return Response.noContent().build();
        }
        String sessionId = request.getSession(true).getId();
        if ("dismiss".equalsIgnoreCase(body.getDecision())) {
            advisor.dismissed(sessionId, body.getKey());
        } else {
            advisor.accepted(sessionId, body.getKey());
        }
        log.debug("Suggestion decision: session={}, key={}, decision={}",
                sessionId, body.getKey(), body.getDecision());
        return Response.noContent().build();
    }

    /** Minimal JSON binding for the chip's {@code { "key": "...", "decision": "..." }} payload. */
    public static class SuggestionDecision {
        private String key;
        private String decision;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getDecision() {
            return decision;
        }

        public void setDecision(String decision) {
            this.decision = decision;
        }
    }
}

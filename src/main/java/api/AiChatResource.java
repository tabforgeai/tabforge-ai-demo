package api;

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
import jakarta.ws.rs.core.MediaType;

/**
 * The {@code POST /api/ai/chat} endpoint that backs the template's slide-out AI panel.
 *
 * <h2>Why this endpoint exists</h2>
 * <p>The template's chat input is transport-agnostic: when the user hits send, the panel emits a
 * {@code user_message} event and waits for the application to render a reply via
 * {@code PFTemplate.aiPanel.streamAssistant(reply)}. It deliberately ships <em>without</em> a chat
 * backend so each application can wire its own. This resource is that wiring for the demo: it forwards
 * the typed text to the session's {@link GlobalAiAssistantBean} and returns the assistant's answer as
 * plain text.</p>
 *
 * <p>The companion {@link AiStreamResource} ({@code GET /api/ai/stream}) is the <em>outbound</em> live
 * feed (AI&rarr;UI activity rows); this resource is the <em>inbound</em> request/response chat
 * (UI&rarr;AI). Together they make the panel both observe and converse.</p>
 *
 * <h2>Familiar analogy</h2>
 * <p>A drive-through intercom. You speak your order at the speaker ({@code POST /chat}); the kitchen
 * (the assistant) prepares it and hands the reply back through the same window. The always-on menu board
 * lighting up with activity is the separate SSE ticker.</p>
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   panel send → InputEventBus('user_message') → fetch POST /api/ai/chat
 *        → this resource reads the HTTP session id
 *        → GlobalAiAssistantBean.ask(sessionId, text)   (assistant sees this session's navigation)
 *        → reply text → PFTemplate.aiPanel.streamAssistant(reply)
 * </pre>
 */
@Path("/ai")
public class AiChatResource {

    private static final Logger log = LoggerFactory.getLogger(AiChatResource.class);

    @Inject
    private GlobalAiAssistantBean globalAssistant;

    /**
     * Handle one chat message from the AI panel.
     *
     * <p>Reads the HTTP session id from the request (there is no {@code FacesContext} on a REST call, so
     * the session is taken straight from {@link HttpServletRequest}) and hands it, with the message, to
     * the session's assistant. Errors are converted to a readable sentence rather than an HTTP 500, so
     * the panel always shows something useful.</p>
     *
     * @param request the HTTP request, used only to obtain the session id that scopes the assistant's
     *                activity context to this user
     * @param body    the JSON payload {@code {"text": "..."}} sent by the panel
     * @return the assistant's reply as plain text, rendered in the chat by {@code streamAssistant(reply)}
     */
    @POST
    @Path("/chat")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String chat(@Context HttpServletRequest request, ChatRequest body) {
        String sessionId = request.getSession(true).getId();
        String text = body != null ? body.getText() : null;
        try {
            return globalAssistant.ask(sessionId, text);
        } catch (Exception ex) {
            log.error("AiChatResource.chat() failed: {}", ex.getMessage(), ex);
            return "Sorry — I couldn't answer that: " + EasyAI.extractErrorMessage(ex);
        }
    }

    /** Minimal JSON binding for the panel's {@code { "text": "..." }} payload. */
    public static class ChatRequest {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}

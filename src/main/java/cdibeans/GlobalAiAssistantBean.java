package cdibeans;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.ai.EasyAI;
import dyntabs.ai.activity.ActivityContext;
import dyntabs.ai.activity.ActivityStore;
import interfaces.NavigatorAssistant;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Session-scoped holder for the global, ambient-aware {@link NavigatorAssistant} behind the template's
 * AI panel.
 *
 * <h2>What this bean is for</h2>
 * <p>It builds the assistant <b>once per HTTP session</b>, binding it to an {@link ActivityContext}
 * scoped to that session (and to no single tab), so the assistant can see the user's navigation across
 * every tracked tab. Because the assistant carries its own chat memory, keeping one instance per session
 * lets the conversation in the panel build up naturally across messages.</p>
 *
 * <p><b>Why the session id is passed in rather than discovered here:</b> the panel's messages arrive over
 * a plain REST call ({@code POST /api/ai/chat}), where there is no {@code FacesContext} to read the
 * session from. The {@code AiChatResource} already has the {@code HttpServletRequest}, so it reads the
 * session id there and hands it to {@link #ask(String, String)}. We scope the {@link ActivityContext}
 * with that exact id — the same id the library stamps onto recorded navigation events — so the read-side
 * and write-side agree on "this session".</p>
 *
 * <p><b>Familiar analogy:</b> a personal concierge assigned to you for the duration of your visit. You
 * can walk up to the front desk (the global panel) at any time; it's the same concierge who has been
 * quietly noting which rooms you've entered, so "where am I?" gets an instant, correct answer.</p>
 *
 * <h2>Who calls it / where the result goes</h2>
 * <p>{@code api.AiChatResource} calls {@link #ask(String, String)}; the returned text is streamed into
 * the chat by {@code PFTemplate.aiPanel.streamAssistant(reply)}.</p>
 *
 * @see NavigatorAssistant
 * @see dyntabs.ai.activity.ActivityContext
 */
@Named("globalAiAssistantBean")
@SessionScoped
public class GlobalAiAssistantBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(GlobalAiAssistantBean.class);

    /** The shared, application-scoped ambient timeline — written by tab navigation, read by our context. */
    @Inject
    private ActivityStore activityStore;

    /** Built lazily on the first message, then reused for the session so chat memory persists. */
    private transient NavigatorAssistant assistant;

    /**
     * Answer one panel message, building the assistant on first use.
     *
     * <p>Called by {@code AiChatResource} for every {@code POST /api/ai/chat}. On the first call it builds
     * the {@link NavigatorAssistant} bound to a session-wide {@link ActivityContext}; subsequent calls
     * reuse it. The assistant re-reads the activity timeline on each call, so the reply always reflects
     * the user's latest navigation.</p>
     *
     * @param sessionId   the HTTP session id (supplied by the REST resource), used to scope the activity
     *                    context to this user's timeline
     * @param userMessage the user's typed question
     * @return the assistant's reply text, shown in the AI panel
     */
    public String ask(String sessionId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "";
        }
        return assistant(sessionId).ask(userMessage.trim());
    }

    /** Lazily create the session's assistant, scoped to this session's activity across all tabs. */
    private NavigatorAssistant assistant(String sessionId) {
        if (assistant == null) {
            ActivityContext activityContext = ActivityContext.of(activityStore)
                    .forSession(sessionId)   // session-wide: no forTab(...), so it spans every tab
                    .build();
            assistant = EasyAI.assistant(NavigatorAssistant.class)
                    .withActivityContext(activityContext)
                    .build();
            log.debug("Built global NavigatorAssistant for session {}", sessionId);
        }
        return assistant;
    }
}

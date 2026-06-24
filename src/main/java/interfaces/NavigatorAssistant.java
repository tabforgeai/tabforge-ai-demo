package interfaces;

import dyntabs.ai.annotation.EasyAIAssistant;

/**
 * The <b>application-wide</b> AI assistant behind the template's slide-out AI panel — the counterpart to
 * the per-tab {@link OrdersAssistant}.
 *
 * <h2>What this interface is for</h2>
 * <p>Where {@link OrdersAssistant} is scoped to a single tab, this one is scoped to the whole
 * <em>session</em>. It is wired (in {@code cdibeans.GlobalAiAssistantBean}) with
 * {@code .withActivityContext(...)} bound to {@code forSession(sessionId)} <b>and no {@code forTab(...)}</b>,
 * so it can see the user's navigation across <em>every</em> tracked tab. That is what lets it answer
 * "where am I?" or "what have I been looking at?" — the most recent {@code NAVIGATION} event in the
 * injected activity briefing tells it which tab is currently in focus.</p>
 *
 * <p>It is deliberately given no tools: this assistant's whole job is to be <em>aware</em> of context,
 * not to act on it (acting-on-deixis is already shown by the Orders tab). Keeping it read-only makes the
 * "it knows where we are" effect unmistakable.</p>
 *
 * <p><b>Familiar analogy:</b> a receptionist with a view of the whole office floor. Ask "which room am I
 * in?" and they answer from what they just watched you walk into — no map, no room number needed.</p>
 *
 * <h2>Who calls it / where the result goes</h2>
 * <p>{@code api.AiChatResource} (the {@code POST /api/ai/chat} endpoint) receives the panel's message and
 * calls {@code GlobalAiAssistantBean.ask(...)}, which calls {@link #ask(String)}. The reply text is sent
 * back over HTTP and rendered in the chat by {@code PFTemplate.aiPanel.streamAssistant(reply)}.</p>
 *
 * @see dyntabs.ai.activity.ActivityContext
 * @see OrdersAssistant the per-tab, tool-enabled sibling
 */
@EasyAIAssistant(systemMessage = """
        You are the assistant for a multi-tab back-office application, reachable from a global panel that
        is always available regardless of which tab the user is in.

        A short log of the user's most recent navigation is provided to you as system context — which
        tabs they opened and selected, most recent last. Use it to stay oriented:
        - When asked "where am I?", "what tab am I on?", or "what am I looking at?", answer with the tab
          named in the MOST RECENT navigation entry.
        - When asked what they have been doing, summarise the recent navigation in plain language.
        - If the activity log is empty, say you don't see any recent navigation yet.

        Be concise and concrete; name the actual tab titles from the log rather than guessing.
        """)
public interface NavigatorAssistant {

    /**
     * Send one message to the global assistant and get its reply.
     *
     * <p>Called by {@code GlobalAiAssistantBean.ask(...)} for each panel submission. EasyAI re-renders the
     * bound session-wide {@code ActivityContext} into the system message just before the model runs, so
     * the answer reflects the user's latest tab navigation. The returned text is shown in the panel.</p>
     *
     * @param userMessage the user's free-text question (e.g. "where am I?", "what have I been looking at?")
     * @return the assistant's natural-language reply
     */
    String ask(String userMessage);
}

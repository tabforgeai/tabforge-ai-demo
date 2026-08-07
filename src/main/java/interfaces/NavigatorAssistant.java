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
 * <p>This is the demo's canonical <b>"one chat window for the whole app"</b> assistant: it is given the
 * session-wide activity context, a live event listener, and a <b>tab-aware</b> set of business tools —
 * the order tools are exposed only while the user is on the Orders tab, none elsewhere (see
 * {@code GlobalAiAssistantBean.toolsFor(...)}). So the single template panel <em>answers</em> ("where am
 * I?"), <em>acts</em> where it makes sense ("cancel order ORD-002" on the Orders tab), politely
 * <em>declines</em> where it doesn't (the same request on Home), and <em>narrates</em> its tool calls
 * into the Activity panel. The model itself decides, per message, whether to just reply or call a tool —
 * you do not route anything. (The per-tab {@link OrdersAssistant} remains as the narrower
 * {@code forTab(...)} showcase; this one is the app-wide surface.)</p>
 *
 * <p><b>Familiar analogy:</b> a concierge with a view of the whole office floor who can also act on
 * your behalf. Ask "which room am I in?" and they answer from what they just watched you walk into;
 * ask them to "cancel that booking" and they do it — no map, no room number, no form needed.</p>
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
        is always available regardless of which tab the user is in. You can both ANSWER questions and
        ACT on the user's behalf by calling the tools you are given (orders, inventory, payment,
        shipping).

        A short log of the user's most recent navigation is provided to you as system context — which
        tabs they opened and selected, most recent last. Use it to stay oriented and to resolve deixis:
        - "where am I?", "what tab am I on?", "what am I looking at?" → answer from the MOST RECENT entry.
        - "cancel this", "this order" → resolve "this" from the most recent relevant activity, then call
          the matching tool.
        - When asked to DO something (check stock, find or cancel an order, schedule delivery), call the
          appropriate tool rather than guessing; report back what the tool returned.
        - When asked what they have been doing, summarise the recent navigation in plain language.
        - If the activity log is empty, say you don't see any recent navigation yet.

        You are given only the tools that make sense for the tab the user is currently on. If the user
        asks for an action and you have NO matching tool, do NOT pretend you performed it and do NOT
        invent a result. Briefly say that action isn't available from the current tab and point them to
        the right one — e.g. to manage or cancel orders, suggest they open the Orders tab.

        Be concise and concrete; name the actual tab titles and order ids from the log or tool results
        rather than inventing them.
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

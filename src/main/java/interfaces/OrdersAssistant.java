package interfaces;

import dyntabs.ai.annotation.EasyAIAssistant;

/**
 * The AI assistant for the "Orders — Ambient Memory" demo tab: a small back-office helper that can
 * answer questions about orders and act on them, <b>without the user having to repeat which order they
 * mean</b>.
 *
 * <h2>What this interface is for</h2>
 * <p>It is the typed front door to the model. {@code EasyAI.assistant(OrdersAssistant.class)} turns it
 * into a runtime proxy; calling {@link #ask(String)} sends the user's text to the LLM and returns the
 * reply. The interesting part is <em>how</em> the proxy is wired in
 * {@code cdibeans.OrdersBean}: it gets {@code .withTools(orderService)} (so the model can actually find
 * and cancel orders) and {@code .withActivityContext(...)} (so a short log of what the user just did in
 * the UI is folded into the system message before every call).</p>
 *
 * <p>That activity log is what makes deixis work. When the user opens order {@code ORD-002} in the
 * table and then types "cancel this", the model never sees an id in the message — it sees one in the
 * injected briefing ("recently viewed order ORD-002") and resolves "this" itself.</p>
 *
 * <p><b>Familiar analogy:</b> a colleague standing behind you who has been watching your screen. You can
 * point at the monitor and say "cancel that one" — they know which order you mean because they saw you
 * open it, so you never read the id aloud.</p>
 *
 * <h2>Who calls it / where the result goes</h2>
 * <p>{@code OrdersBean.ask()} (a JSF action behind the chat's send button) calls {@link #ask(String)};
 * the returned text is appended to the on-screen conversation log in the tab.</p>
 *
 * @see dyntabs.ai.activity.ActivityContext
 * @see services.OrderActivityService the source of the "viewed order" activity that resolves "this"
 */
@EasyAIAssistant(systemMessage = """
        You are an orders assistant for a back-office application.
        The user is working in a UI, and a short log of their most recent actions is provided to you
        as system context (which tab they opened, which order they just viewed).

        When the user refers to "this", "that", "this order", "it", or similar without giving an order
        id, resolve the reference to the MOST RECENT order in that activity log, then use your tools to
        act on it. Always state explicitly which order id you acted on. If the activity log contains no
        order and the user gives no id, ask them which order they mean.
        """)
public interface OrdersAssistant {

    /**
     * Send one user message to the assistant and get its reply.
     *
     * <p>Called by {@code OrdersBean.ask()} on each chat submission. Before the model runs, EasyAI
     * re-renders the bound {@code ActivityContext} into the system message, so the answer reflects the
     * user's latest UI activity. The returned string is shown in the tab's conversation panel.</p>
     *
     * @param userMessage the user's free-text request (e.g. "cancel this", "what is the status of this order?")
     * @return the assistant's natural-language reply, already reflecting any tool calls it made
     */
    String ask(String userMessage);
}

package api;

import dyntabs.ai.event.EasyAIEvent;
import dyntabs.ai.event.EasyAIListener;

/**
 * The single translation point between the EasyAI library and the pf-modern-template
 * Activity Panel. It implements {@link EasyAIListener}, so EasyAI hands it a live stream of
 * {@link EasyAIEvent}s as any capability runs; for each one it builds the matching
 * {@link AgentEvent} (the template's JSON shape) and pushes it to the right browser over SSE.
 *
 * <h2>Why this class is the ONLY place the two worlds meet</h2>
 * The EasyAI core is deliberately transport-agnostic — it knows nothing about SSE, HTTP, or
 * the template's JSON schema. The template, conversely, knows nothing about EasyAI's phases.
 * This bridge is the one adapter that speaks both languages; keeping the mapping here (and only
 * here) is what lets the library stay a pure dependency and the demo own all the UI wiring.
 *
 * <h2>Familiar analogy</h2>
 * A <b>court interpreter</b>: the witness (EasyAI) testifies in its own language (phases:
 * STARTED, STEP, RETRY…); the jury (the Activity Panel) only understands another (event types:
 * agent_started, tool_call, warning…). The interpreter renders each sentence faithfully, adding
 * nothing and skipping nothing, so both sides stay blissfully ignorant of each other.
 *
 * <h2>Lifecycle &amp; threading</h2>
 * One bridge is created per operation, bound to a {@code sessionId} (so events reach the user who
 * triggered the work) and the shared {@link AiEventChannel}. It is a plain object, not a CDI bean,
 * because it closes over a request-specific session id. {@link #onEvent} runs on whatever thread
 * EasyAI is working on; it does no blocking work itself — it just builds a small object and asks
 * the channel to broadcast, and the channel safely no-ops if that session has no open stream.
 *
 * <h2>The mapping (EasyAI {@code phase}/{@code status} → template {@code type})</h2>
 * <table>
 *   <tr><th>EasyAIEvent phase</th><th>AgentEvent</th></tr>
 *   <tr><td>STARTED</td><td>agent_started (running)</td></tr>
 *   <tr><td>STEP_STARTED</td><td>tool_call (running spinner)</td></tr>
 *   <tr><td>STEP</td><td>tool_result (success / error)</td></tr>
 *   <tr><td>PROGRESS</td><td>reasoning (neutral info row)</td></tr>
 *   <tr><td>RESULT (CHAT)</td><td>assistant_message (chat bubble)</td></tr>
 *   <tr><td>RESULT (other)</td><td>tool_result (success)</td></tr>
 *   <tr><td>RETRY</td><td>warning (amber)</td></tr>
 *   <tr><td>FINISHED</td><td>agent_finished (success)</td></tr>
 *   <tr><td>ERROR</td><td>error (red)</td></tr>
 * </table>
 *
 * @see EasyAIListener the core-side contract this implements
 * @see AgentEvent the template-side wire format it produces
 */
public class EasyAiActivityBridge implements EasyAIListener {

    private final AiEventChannel channel;
    private final String sessionId;

    /**
     * Create a bridge for one user's operation.
     *
     * @param channel   the application-scoped SSE registry that fans events out to browsers
     * @param sessionId the HTTP session id of the user who triggered the work (the SSE target)
     */
    public EasyAiActivityBridge(AiEventChannel channel, String sessionId) {
        this.channel = channel;
        this.sessionId = sessionId;
    }

    /**
     * Receive one EasyAI event, translate it to the template format, and broadcast it.
     *
     * <p>Called by EasyAI's {@code EventEmitter} once per lifecycle moment. Never throws: the
     * emitter already shields the operation from listener exceptions, and the channel itself
     * tolerates a missing stream — so this method is safe to call from inside a running AI op.</p>
     *
     * @param event the immutable event describing what just happened in the EasyAI core
     */
    @Override
    public void onEvent(EasyAIEvent event) {
        String agent = agentLabel(event.source());
        AgentEvent out = switch (event.phase()) {
            case STARTED      -> AgentEvent.agentStarted(agent, event.title());
            case STEP_STARTED -> AgentEvent.toolCall(agent, event.toolName(),
                                     labelOrTool(event), event.detail());
            case STEP         -> AgentEvent.toolResult(agent, event.toolName(),
                                     isError(event) ? "error" : "success",
                                     labelOrTool(event), event.detail());
            case PROGRESS     -> AgentEvent.reasoning(agent, event.title(), event.detail());
            case RETRY        -> AgentEvent.warning(agent, event.title(), event.detail());
            case FINISHED     -> AgentEvent.agentFinished(agent, event.title());
            case ERROR        -> AgentEvent.error(agent, event.title(), event.detail());
            case RESULT       -> event.source() == EasyAIEvent.Source.CHAT
                                     // chat's reply rides in detail → route to the chat bubble
                                     ? AgentEvent.assistantMessage(event.detail())
                                     // any other capability's result → a green success row
                                     : AgentEvent.toolResult(agent, event.toolName(),
                                           "success", event.title(), event.detail());
        };
        channel.emit(sessionId, out);
    }

    /** True when the event's status indicates a failed step. */
    private static boolean isError(EasyAIEvent event) {
        return event.status() == EasyAIEvent.Status.ERROR;
    }

    /** Prefer the tool name for a row label; fall back to the title if no tool was named. */
    private static String labelOrTool(EasyAIEvent event) {
        return event.toolName() != null ? event.toolName() : event.title();
    }

    /** Friendly, capability-specific name shown as the "agent" in the timeline. */
    private static String agentLabel(EasyAIEvent.Source source) {
        return switch (source) {
            case CHAT      -> "Chat";
            case ASSISTANT -> "Assistant";
            case RAG       -> "RAG";
            case AGENT     -> "Agent";
            case INDEXER   -> "Indexer";
            case EXTRACT   -> "Extractor";
        };
    }
}

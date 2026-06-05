package api;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One event pushed from the backend to the pf-modern-template Activity Panel over the
 * SSE channel ({@code GET /api/ai/stream}).
 *
 * <h2>What this class really is</h2>
 * This is the <b>template's JSON wire format</b> — nothing more. The pf-modern-template
 * front-end does not know (or care) whether an event came from an LLM, a rule engine, or a
 * batch job; it only knows how to render a JSON object with these fields. This class is the
 * Java mirror of that contract, so the demo can build well-formed events with type safety
 * instead of hand-assembling JSON strings.
 *
 * <h2>Familiar analogy</h2>
 * Think of it as a <b>shipping label</b>: the warehouse (EasyAI core) does the real work, but
 * the courier (the Activity Panel) only reads the label fields — {@code type}, {@code status},
 * {@code title} — to decide which shelf (row, icon, color) the parcel goes on.
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   EasyAIEvent (core)
 *        → EasyAiActivityBridge.onEvent(..)        (maps phase/status → these fields)
 *        → AiEventChannel.emit(sessionId, AgentEvent)
 *        → SSE (JSON, serialized by JSON-B)
 *        → browser: PFTemplate.AgentTransport → AgentEventBus → layout.js renders it
 * </pre>
 *
 * <h2>How the client interprets {@link #type}</h2>
 * <ul>
 *   <li>{@code "assistant_message"} → routed into the chat bubble; uses {@link #text}.</li>
 *   <li>everything else ({@code "agent_started"}, {@code "tool_call"}, {@code "tool_result"},
 *       {@code "reasoning"}, {@code "agent_finished"}, {@code "error"}, {@code "warning"}) →
 *       a row in the Activity tab, with icon/color chosen from {@link #type}/{@link #status}.</li>
 * </ul>
 *
 * Only non-null fields are serialized (JSON-B skips nulls), so each factory sets just the
 * fields meaningful for that event type. Instances are created through the static factories,
 * never the bare constructor, so {@link #id} and {@link #timestamp} are always populated.
 */
public class AgentEvent {

    /** Process-wide counter giving every event a unique, ordered id (per type). */
    private static final AtomicLong SEQ = new AtomicLong();

    private String id;
    private String type;
    private String status;
    private String title;
    private String agent;
    private String tool;
    private String details;
    private String text;
    private String timestamp;

    /** Required by JSON-B. Application code uses the static factories instead. */
    public AgentEvent() {
    }

    private static String nextId(String type) {
        return type + '-' + SEQ.incrementAndGet();
    }

    /**
     * The agent/operation has begun — the first Activity row (spinning border).
     *
     * @param agent the logical source name shown in the timeline (e.g. {@code "Agent"})
     * @param title short human-readable label (e.g. {@code "Planning task"})
     * @return a populated {@code agent_started} event with {@code running} status
     */
    public static AgentEvent agentStarted(String agent, String title) {
        AgentEvent e = base("agent_started", "running");
        e.agent = agent;
        e.title = title;
        return e;
    }

    /**
     * A tool is about to be called — a row with a spinning border (work in progress).
     * Paired later with a {@link #toolResult} row carrying the outcome.
     *
     * @param agent   the source that made the call
     * @param tool    the tool/method name (drives the icon: {@code database}→🗄, {@code email}→✉, …)
     * @param title   short label (usually the tool name)
     * @param details a short summary of the arguments
     * @return a populated {@code tool_call} event with {@code running} status
     */
    public static AgentEvent toolCall(String agent, String tool, String title, String details) {
        AgentEvent e = base("tool_call", "running");
        e.agent = agent;
        e.tool = tool;
        e.title = title;
        e.details = details;
        return e;
    }

    /**
     * The outcome of a tool call — a row with a green check (success) or red mark (error).
     *
     * @param agent   the source that made the call
     * @param tool    the tool/method name (icon resolution)
     * @param status  {@code "success"} or {@code "error"}
     * @param title   short label (e.g. the tool name)
     * @param details a short "result" summary
     * @return a populated {@code tool_result} event
     */
    public static AgentEvent toolResult(String agent, String tool, String status, String title, String details) {
        AgentEvent e = base("tool_result", status);
        e.agent = agent;
        e.tool = tool;
        e.title = title;
        e.details = details;
        return e;
    }

    /**
     * An informational progress note ("thinking out loud") — a neutral Activity row.
     * Used for EasyAI {@code PROGRESS} phases (e.g. "Loading document", "Querying model").
     *
     * @param agent   the source
     * @param title   short label
     * @param details optional detail line (may be null)
     * @return a populated {@code reasoning} event with {@code running} status
     */
    public static AgentEvent reasoning(String agent, String title, String details) {
        AgentEvent e = base("reasoning", "running");
        e.agent = agent;
        e.title = title;
        e.details = details;
        return e;
    }

    /**
     * A non-fatal hiccup the operation recovered (or is recovering) from — an amber row.
     * Used for EasyAI {@code RETRY} phases (e.g. "Retrying extraction").
     *
     * @param agent   the source
     * @param title   short label
     * @param details what went wrong / what is being retried
     * @return a populated {@code warning} event
     */
    public static AgentEvent warning(String agent, String title, String details) {
        AgentEvent e = base("warning", "warning");
        e.agent = agent;
        e.title = title;
        e.details = details;
        return e;
    }

    /**
     * The assistant's final reply — rendered in the chat bubble, not the Activity tab.
     *
     * @param text the reply text (Markdown supported on the client)
     * @return a populated {@code assistant_message} event
     */
    public static AgentEvent assistantMessage(String text) {
        AgentEvent e = base("assistant_message", null);
        e.text = text;
        return e;
    }

    /**
     * The operation finished cleanly — the closing Activity row (green).
     *
     * @param agent the source name
     * @param title short label (e.g. {@code "Task complete"})
     * @return a populated {@code agent_finished} event with {@code success} status
     */
    public static AgentEvent agentFinished(String agent, String title) {
        AgentEvent e = base("agent_finished", "success");
        e.agent = agent;
        e.title = title;
        return e;
    }

    /**
     * Something failed — a red Activity row.
     *
     * @param agent   the source name
     * @param title   short label (e.g. {@code "Agent failed"})
     * @param details the readable error message
     * @return a populated {@code error} event
     */
    public static AgentEvent error(String agent, String title, String details) {
        AgentEvent e = base("error", "error");
        e.agent = agent;
        e.title = title;
        e.details = details;
        return e;
    }

    /** Shared initializer: sets type, status, id and timestamp. */
    private static AgentEvent base(String type, String status) {
        AgentEvent e = new AgentEvent();
        e.type = type;
        e.status = status;
        e.id = nextId(type);
        e.timestamp = Instant.now().toString();
        return e;
    }

    // ── Getters (read by JSON-B during serialization) ──────────────────────────
    public String getId()        { return id; }
    public String getType()      { return type; }
    public String getStatus()    { return status; }
    public String getTitle()     { return title; }
    public String getAgent()     { return agent; }
    public String getTool()      { return tool; }
    public String getDetails()   { return details; }
    public String getText()      { return text; }
    public String getTimestamp() { return timestamp; }
}

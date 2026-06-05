package api;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseBroadcaster;
import jakarta.ws.rs.sse.SseEventSink;

/**
 * Application-wide registry that maps each HTTP session to its open Server-Sent
 * Events connection(s), and pushes {@link AgentEvent}s to the right session.
 *
 * <h2>Why per-session</h2>
 * Two users (two browsers) hitting the demo at once must each see only their own activity.
 * The HTTP session id is the natural correlation key: the browser's {@code EventSource} GET
 * and the JSF/REST POSTs all carry the same {@code JSESSIONID} cookie (same origin), so the
 * stream and the work that feeds it line up automatically.
 *
 * <h2>Familiar analogy</h2>
 * A <b>hotel switchboard</b>: each guest (session) has a room phone (SSE connection). When the
 * kitchen (EasyAI) finishes a dish it tells the switchboard the room number; the switchboard
 * rings only that room. Unknown room → the message is quietly dropped, the kitchen never blocks.
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   AiStreamResource (GET /api/ai/stream)   → subscribe(sessionId, sse, sink)
 *   EasyAiActivityBridge (via AiBean)        → emit(sessionId, event)  → browser
 * </pre>
 *
 * One {@link SseBroadcaster} is kept per session id (so multiple tabs of the same user all
 * receive events, and dead connections are pruned by the broadcaster). The {@link Sse} factory
 * is captured from the first subscription; it is effectively a per-application singleton.
 *
 * Thread-safe: the map is a {@link ConcurrentHashMap} and {@code SseBroadcaster} is itself safe
 * for concurrent broadcast.
 */
@ApplicationScoped
public class AiEventChannel {

    private static final Logger log = LoggerFactory.getLogger(AiEventChannel.class);

    /** sessionId → broadcaster fanning out to every open SSE connection of that session. */
    private final Map<String, SseBroadcaster> broadcasters = new ConcurrentHashMap<>();

    /** Captured from the first {@link #subscribe} call; used to build outbound events. */
    private volatile Sse sse;

    /**
     * Register a freshly opened SSE connection for a session.
     *
     * Called once per {@code EventSource} connection from {@link AiStreamResource} (and again
     * automatically by the browser after a reconnect).
     *
     * @param sessionId the HTTP session id correlating this stream with the user's work
     * @param sse       the JAX-RS SSE factory (used to lazily create the broadcaster)
     * @param sink      the per-connection output channel to register with the broadcaster
     */
    public void subscribe(String sessionId, Sse sse, SseEventSink sink) {
        this.sse = sse;
        SseBroadcaster broadcaster = broadcasters.computeIfAbsent(sessionId, id -> {
            SseBroadcaster b = sse.newBroadcaster();
            b.onClose(s -> log.debug("SSE sink closed for session {}", id));
            b.onError((s, e) -> log.debug("SSE sink error for session {}: {}", id, e.toString()));
            return b;
        });
        broadcaster.register(sink);
        log.debug("SSE subscribed: session {}", sessionId);
    }

    /**
     * Push one event to every open SSE connection of the given session.
     *
     * No-op (with a debug log) if that session currently has no open stream — e.g. the panel was
     * never opened, or the connection dropped. The work side is therefore never blocked or broken
     * by a missing listener.
     *
     * @param sessionId the target session (same id used at {@link #subscribe})
     * @param event     the event to serialize (as JSON) and send
     */
    public void emit(String sessionId, AgentEvent event) {
        SseBroadcaster broadcaster = broadcasters.get(sessionId);
        if (broadcaster == null || sse == null) {
            log.debug("No SSE listener for session {} — dropping {} event", sessionId, event.getType());
            return;
        }
        OutboundSseEvent sseEvent = sse.newEventBuilder()
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(AgentEvent.class, event)
                .build();
        broadcaster.broadcast(sseEvent);
    }
}

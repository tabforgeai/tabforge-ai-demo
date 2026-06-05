package api;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

/**
 * Long-lived Server-Sent Events endpoint the Activity Panel connects to once at page load.
 *
 * <h2>Why a separate, always-open channel</h2>
 * The user triggers work through ordinary JSF postbacks (e.g. "run agent"). That request blocks
 * until the AI finishes. If activity had to ride back on that same response, the panel could only
 * update once, at the end. Instead the browser holds a second, permanently-open SSE connection
 * here; EasyAI pushes events onto it <em>while</em> the JSF request is still running, so rows
 * appear live, one by one.
 *
 * <h2>Familiar analogy</h2>
 * A <b>live sports ticker</b>: you are watching the match (the blocking JSF request), but a
 * separate scrolling banner (this SSE stream) updates play-by-play without waiting for the final
 * whistle.
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   browser: PFTemplate.AgentTransport.connectSSE('/api/ai/stream')   (EventSource, GET)
 *        → this resource registers the connection in {@link AiEventChannel} under the session id
 *        → the connection stays open; events are pushed later by the EasyAI activity bridge
 * </pre>
 *
 * {@code EventSource} reconnects on its own if the connection drops; each reconnect simply
 * registers a new sink with the session's broadcaster.
 */
@Path("/ai")
public class AiStreamResource {

    @Inject
    private AiEventChannel channel;

    /**
     * Open the SSE stream for the calling session.
     *
     * The method returns immediately, but the {@code sink} is kept open by the JAX-RS runtime and
     * by {@link AiEventChannel}; the browser receives events until it closes the {@code EventSource}
     * (e.g. on navigation).
     *
     * @param sse     JAX-RS SSE factory (injected) — passed to the channel to build events
     * @param sink    the output channel for this connection (injected) — registered with the channel
     * @param request used only to obtain the HTTP session id that correlates this stream with work
     */
    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void stream(@Context Sse sse,
                       @Context SseEventSink sink,
                       @Context HttpServletRequest request) {
        String sessionId = request.getSession(true).getId();
        channel.subscribe(sessionId, sse, sink);
    }
}

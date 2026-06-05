package api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Activates Jakarta REST (JAX-RS) for the demo and roots every REST resource under {@code /api}.
 *
 * <h2>Why this class exists</h2>
 * A Jakarta EE server will not expose any {@code @Path} resource until <em>some</em> class
 * extends {@link Application} (or a {@code web.xml} servlet mapping does the same job). This
 * single, empty class is that switch: with it on the classpath the server scans the WAR for
 * {@code @Path}-annotated resources and publishes them, all sharing the {@code /api} prefix.
 *
 * <h2>Familiar analogy</h2>
 * Think of it as the <b>main breaker in a fuse box</b>: it has no logic of its own, but until
 * it is flipped on, none of the wired-up circuits ({@link AiStreamResource}) carry any current.
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   server boot → finds RestApplication → scans @Path classes → /api/ai/stream is live
 * </pre>
 */
@ApplicationPath("/api")
public class RestApplication extends Application {
}

package services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.ai.activity.ActivityTracked;
import dyntabs.ai.activity.UserActivityEvent.Type;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * The UI-facing "what the user just did with an order" service for the Ambient Activity Memory demo.
 *
 * <h2>What this class is for</h2>
 * <p>It exists to demonstrate <b>automatic activity capture</b> via {@link ActivityTracked}. When the
 * user clicks a row in the Orders table, {@code OrdersBean} calls {@link #viewOrder(String)} on this
 * bean. Because the method is annotated, the library's interceptor records a
 * {@code BUSINESS_ACTION} event ("viewed order ORD-002") into the ambient timeline automatically — the
 * application code never touches the {@code ActivityRecorder} itself.</p>
 *
 * <p>It is kept deliberately separate from {@code OrderService}: this class models <em>UI actions the
 * user performs</em> (and that we want to remember), whereas {@code OrderService} models <em>operations
 * the AI may perform</em> (the assistant's tools). Splitting them keeps "what the user did" and "what
 * the AI can do" from leaking into each other.</p>
 *
 * <p><b>Familiar analogy:</b> a turnstile with a badge reader. Walking through to look at an order trips
 * the reader, which silently logs "this person visited order ORD-002 at 14:03". You did nothing extra;
 * the act of passing through was the record.</p>
 *
 * <h2>How the recorded result is used</h2>
 * <p>The event lands in the application-scoped {@code InMemoryActivityStore}. Later, when the user asks
 * the {@link interfaces.OrdersAssistant} something like "cancel this", the bound {@code ActivityContext}
 * reads that very event back and injects it into the prompt, letting the model resolve "this" to
 * ORD-002.</p>
 *
 * @see ActivityTracked
 * @see interfaces.OrdersAssistant
 */
@ApplicationScoped
public class OrderActivityService {

    private static final Logger log = LoggerFactory.getLogger(OrderActivityService.class);

    /**
     * Open/inspect a single order in the UI — and, as a side effect of being {@code @ActivityTracked},
     * remember that the user did so.
     *
     * <p>Called by {@code OrdersBean.openOrder(...)} when a table row is selected. The annotation tells
     * the interceptor to record a {@code BUSINESS_ACTION} with verb {@code "view"} and an
     * {@code EntityRef} of type {@code "order"} whose id is taken from the {@code orderId} parameter
     * (matched by name — the {@code -parameters} compiler flag is enabled in this project). The returned
     * summary is shown in the tab's detail panel; the recorded event is what later resolves "this order".</p>
     *
     * @param orderId the id of the order the user opened (e.g. {@code "ORD-002"})
     * @return a short human-readable summary of the order, for display in the detail panel
     */
    @ActivityTracked(type = Type.BUSINESS_ACTION, verb = "view", entityType = "order", entityIdParams = "orderId")
    public String viewOrder(String orderId) {
        log.debug("viewOrder(orderId={})", orderId);
        return "Order " + orderId + " — opened for review.";
    }
}

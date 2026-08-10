package services;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import model.DemoOrder;

/**
 * The demo's <b>single source of truth</b> for the sample orders — one small, application-scoped
 * catalogue that both the UI and the proactive-suggestion machinery read from.
 *
 * <h2>Why a shared catalogue (and not the old private list)</h2>
 * <p>The Orders table used to build its rows from a {@code sampleOrders()} method hidden inside
 * {@code OrdersBean}. That was fine while only the table needed the data — but a proactive rule such as
 * "the user has been looking at several <em>Acme</em> orders" needs to answer a question the recorded
 * activity alone cannot: <em>which customer owns ORD-004?</em> The captured activity event carries only
 * the order id, not the customer. Rather than bloat every recorded event with a customer field, we give
 * the rule a place to look the id up — this catalogue. UI and rule now agree on the same facts because
 * they read the same source.</p>
 *
 * <p><b>Familiar analogy:</b> the shop's order ledger kept at the front desk. The cashier (the table)
 * and the back-office analyst (a suggestion rule) both consult the one ledger, so they never disagree
 * about who placed which order.</p>
 *
 * <h2>Note the deliberately shared customer</h2>
 * <p>{@code ORD-001} and {@code ORD-004} are both <b>Acme Corp</b>. That is not an accident: it is what
 * lets the demo's {@link SameCustomerOrdersRule} fire when the user opens those two rows in a row, so the
 * whole proactive chain (detect → phrase → suggest) has something real to react to.</p>
 *
 * <h2>Place in the flow</h2>
 * <pre>
 *   OrdersBean.getOrders()          → all()            → renders the table
 *   SameCustomerOrdersRule.evaluate → customerOf(id)   → groups recent views by customer
 * </pre>
 *
 * <p>Application-scoped and read-only after construction, so it is safe to share across sessions and
 * threads without synchronization.</p>
 */
@ApplicationScoped
public class OrderCatalog {

    private static final Logger log = LoggerFactory.getLogger(OrderCatalog.class);

    /** The demo's fixed order set — immutable after {@link #seed()} so it needs no locking. */
    private List<DemoOrder> orders;

    /** Seed the fixed demo orders once, at application startup. */
    @PostConstruct
    void seed() {
        this.orders = List.of(
                new DemoOrder("ORD-001", "Acme Corp",    "Shipped",         1299.00),
                new DemoOrder("ORD-002", "Globex LLC",    "Processing",       459.90),
                new DemoOrder("ORD-003", "Initech",       "Pending payment",   88.50),
                // Same customer as ORD-001 on purpose — this is what the same-customer rule detects.
                new DemoOrder("ORD-004", "Acme Corp",     "Delivered",       2750.00));
        log.debug("OrderCatalog seeded with {} orders", orders.size());
    }

    /**
     * @return every demo order, in a stable display order; the value backing the Orders table.
     */
    public List<DemoOrder> all() {
        return orders;
    }

    /**
     * Look up a single order by its code.
     *
     * @param orderId the order code (e.g. {@code "ORD-004"}); {@code null} yields an empty result
     * @return the matching order, or empty if no such id is known
     */
    public Optional<DemoOrder> find(String orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return orders.stream().filter(o -> orderId.equals(o.getId())).findFirst();
    }

    /**
     * The lookup the proactive rule needs: which customer owns this order.
     *
     * @param orderId the order code seen in a recorded "viewed order" activity event
     * @return the owning customer's name, or {@code null} if the id is unknown
     */
    public String customerOf(String orderId) {
        return find(orderId).map(DemoOrder::getCustomer).orElse(null);
    }
}

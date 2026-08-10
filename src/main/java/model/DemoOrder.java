package model;

import java.io.Serializable;

/**
 * One row of the demo "Orders" table — a tiny, immutable value object holding just enough of an
 * order to display it and to let the AI act on it (an id, a customer, a status, a total).
 *
 * <h2>Why this exists as its own top-level type</h2>
 * <p>It used to be a nested class inside {@code OrdersBean}. It was lifted out so that <em>two</em>
 * collaborators can share one definition of "what an order looks like": the {@code OrdersBean} that
 * renders the table, and the {@link services.OrderCatalog} that is the single source of truth for the
 * demo's sample orders. Sharing the type is what lets a proactive rule ask "which customer owns
 * ORD-004?" and get the same answer the table shows.</p>
 *
 * <p><b>Familiar analogy:</b> a printed index card for one order. The card itself never changes once
 * written (immutable); anyone — the clerk at the desk (the table) or the analyst in the back office
 * (a suggestion rule) — can read the same card and agree on the facts.</p>
 *
 * <p>The id is a clean order code (e.g. {@code "ORD-004"}) the assistant can act on directly, which is
 * why the demo uses this rather than the unrelated {@code model.Order} image-gallery type.</p>
 */
public final class DemoOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String customer;
    private final String status;
    private final double total;

    /**
     * Create one immutable order row.
     *
     * @param id       the order code shown in the table and acted on by the AI (e.g. {@code "ORD-004"})
     * @param customer the customer that placed it (e.g. {@code "Acme Corp"}) — the key a same-customer rule groups by
     * @param status   the human-readable status (e.g. {@code "Delivered"})
     * @param total    the order total, in the demo's single currency
     */
    public DemoOrder(String id, String customer, String status, double total) {
        this.id = id;
        this.customer = customer;
        this.status = status;
        this.total = total;
    }

    /** @return the order code (e.g. {@code "ORD-004"}); also the table {@code rowKey} and the AI's handle on it. */
    public String getId() {
        return id;
    }

    /** @return the customer that placed the order (the field a same-customer suggestion rule groups by). */
    public String getCustomer() {
        return customer;
    }

    /** @return the human-readable status shown in the table (e.g. {@code "Pending payment"}). */
    public String getStatus() {
        return status;
    }

    /** @return the order total in the demo's single currency. */
    public double getTotal() {
        return total;
    }
}

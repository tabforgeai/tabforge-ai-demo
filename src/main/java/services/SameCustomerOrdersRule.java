package services;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.ai.activity.EntityRef;
import dyntabs.ai.activity.UserActivityEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The demo's one concrete {@link SuggestionRule}: <b>"the user has been looking at several orders for the
 * same customer — offer to summarize that customer's account."</b>
 *
 * <h2>What it detects</h2>
 * <p>It scans the recent activity window for "viewed order" events (recorded by
 * {@link OrderActivityService#viewOrder(String)}), resolves each order id to its customer via the shared
 * {@link OrderCatalog}, and fires when the <em>most recently</em> viewed order's customer now has
 * {@value #MIN_ORDERS} or more distinct orders viewed within {@link #WINDOW}. Tying the trigger to the
 * newest view means the suggestion appears exactly when the user opens the second Acme order — not on
 * some unrelated later click.</p>
 *
 * <h2>Why the catalog lookup</h2>
 * <p>The recorded activity event carries only the order id, not the customer (we chose not to bloat every
 * event with a customer field). The rule is the domain expert, so it does the lookup itself — the classic
 * shape of a good rule: cheap, deterministic, and grounded in the app's own data. See
 * {@link OrderCatalog}.</p>
 *
 * <p><b>Familiar analogy:</b> a floor-walker in a showroom who notices you have picked up three items all
 * from the same brand, and only then steps over to say "shopping the Acme range? I can pull the whole
 * catalogue for you." One brand, a couple of glances — that is the whole trigger.</p>
 *
 * @see SuggestionRule the contract and the "rule decides, model only phrases" discipline
 * @see SuggestionAdvisor the engine that phrases and pushes what this returns
 */
@ApplicationScoped
public class SameCustomerOrdersRule implements SuggestionRule {

    private static final Logger log = LoggerFactory.getLogger(SameCustomerOrdersRule.class);

    /** How far back to look. Proactivity should react to a burst of interest, not ancient history. */
    private static final Duration WINDOW = Duration.ofMinutes(2);

    /** How many distinct same-customer orders make a "pattern" worth suggesting on. */
    private static final int MIN_ORDERS = 2;

    /** The stable machine name for this kind of suggestion. */
    static final String ACTION_ID = "summarize-customer";

    /** Shared source of truth mapping order id → customer (the same data the table renders). */
    @Inject
    private OrderCatalog orderCatalog;

    @Override
    public Optional<Suggestion> evaluate(List<UserActivityEvent> recent) {
        if (recent == null || recent.isEmpty()) {
            return Optional.empty();
        }

        Instant cutoff = Instant.now().minus(WINDOW);

        // Walk newest → oldest (the store returns oldest first), grouping distinct viewed order ids by
        // customer. The customer of the FIRST qualifying (newest) event is the "trigger" — the suggestion
        // is about what the user is looking at right now.
        Map<String, Set<String>> ordersByCustomer = new LinkedHashMap<>();
        String triggerCustomer = null;

        for (int i = recent.size() - 1; i >= 0; i--) {
            UserActivityEvent e = recent.get(i);
            if (e.timestamp().isBefore(cutoff)) {
                break; // older than the window — and everything before it is older still
            }
            if (e.type() != UserActivityEvent.Type.BUSINESS_ACTION || !"view".equals(e.verb())) {
                continue;
            }
            EntityRef ref = e.primaryEntity();
            if (ref == null || !"order".equals(ref.type())) {
                continue;
            }
            String customer = orderCatalog.customerOf(ref.id());
            if (customer == null) {
                continue; // unknown order id — ignore rather than guess
            }
            ordersByCustomer.computeIfAbsent(customer, k -> new LinkedHashSet<>()).add(ref.id());
            if (triggerCustomer == null) {
                triggerCustomer = customer;
            }
        }

        if (triggerCustomer == null) {
            return Optional.empty();
        }
        Set<String> ids = ordersByCustomer.get(triggerCustomer);
        if (ids == null || ids.size() < MIN_ORDERS) {
            return Optional.empty();
        }

        String idList = ids.stream().sorted().collect(Collectors.joining(", "));
        String key = ACTION_ID + ":" + triggerCustomer;

        // promptForAccept: what runs in the chat if the user says yes — an ordinary request the existing
        // tab-aware assistant already knows how to fulfil with its order tools.
        String promptForAccept = "Summarize the account for " + triggerCustomer
                + ": give me the current status of orders " + idList + " and flag anything that needs attention.";

        // phrasingBrief: facts only. The LLM turns this into one friendly sentence; it decides nothing.
        String phrasingBrief = "The user has just viewed " + ids.size() + " different orders for the same "
                + "customer, " + triggerCustomer + " (orders: " + idList + "). In ONE short, friendly "
                + "sentence, offer to summarize that customer's account. Do not invent any order details "
                + "or numbers; only offer the summary.";

        log.debug("SameCustomerOrdersRule fired: customer={}, orders={}", triggerCustomer, idList);
        return Optional.of(new Suggestion(ACTION_ID, key, promptForAccept, phrasingBrief));
    }
}

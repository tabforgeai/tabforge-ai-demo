package services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.ai.annotation.EasyTool;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/**
 * <b>Deliberately an EJB</b> ({@code @Stateless}), while the other demo services are plain
 * CDI beans ({@code @ApplicationScoped}).
 *
 * <p>This one is kept as a session bean on purpose, to show that EasyAI discovers
 * {@code @EasyTool} methods on EJBs too: at runtime {@code OrdersBean} injects this as a
 * container proxy and passes it to {@code .withTools(orderService)}. EasyAI's tool
 * introspection unwraps the CDI/EJB proxy to find the real methods, so an application that
 * already has its business logic in EJBs does not have to rewrite anything to expose a few
 * methods as AI tools. Think of it as a certified translator standing next to a sealed
 * diplomatic envelope (the proxy): EasyAI reads what is really inside, not just the seal.</p>
 *
 * <p>Everything else in this package uses {@code @ApplicationScoped} — the idiomatic modern
 * default. The point of the mix is not "always use EJBs", it is "you don't have to give them
 * up to adopt EasyAI".</p>
 */
@Stateless
public class OrderService {
	private static final Logger log = LoggerFactory.getLogger(OrderService.class);

	/**
	 * The demo's single source of truth for orders. An EJB may inject a CDI bean, so this
	 * {@code @Stateless} tool reads the <em>same</em> ledger the Orders table renders from —
	 * which is what lets the assistant compare a scanned invoice against the real order total.
	 */
	@Inject
	private OrderCatalog catalog;

	/**
	 * Look up one order and report the fields the assistant needs to reason and compare.
	 *
	 * <p>Previously this returned a canned "shipped, arrives tomorrow" string, so the AI could
	 * never see an amount. Now it reads {@link OrderCatalog} and returns the customer, status and
	 * <b>total</b> — so asking "does this invoice match the order?" has a number on both sides.</p>
	 *
	 * <p><b>Analogy:</b> before, the clerk recited the same sentence to everyone; now the clerk
	 * actually pulls the order card and reads you what is on it.</p>
	 *
	 * @param orderId the order code (e.g. {@code "ORD-004"})
	 * @return a one-line fact sheet, or a clear "not found" message the AI can relay
	 */
	@EasyTool("Finds an order by its ID and returns its customer, status and total amount")
	public String findOrder(String orderId) {
		return catalog.find(orderId)
				.map(o -> String.format("Order %s: customer=%s, status=%s, total=%.2f",
						o.getId(), o.getCustomer(), o.getStatus(), o.getTotal()))
				.orElse("No order found with id " + orderId);
	}

	@EasyTool("Cancels an active order")
	public String cancelOrder(String orderId) {
		return "Order " + orderId + " has been cancelled";
	}

	/**
	 * Open a claim (<i>reklamacija</i>) against an order — the action a proactive "invoice doesn't match
	 * the order" suggestion carries out when the user accepts it.
	 *
	 * <p>This is the far end of the vision "wow": after {@code SuggestionAdvisor} deterministically spots
	 * that a scanned invoice amount differs from the order total and pushes an Accept/Dismiss chip,
	 * accepting injects a prompt that lands here as a tool call. The method validates the order exists,
	 * mints a claim reference, and returns a confirmation sentence the assistant relays back. It changes
	 * no real state (demo), but it models the "draft/record an action" step cleanly.</p>
	 *
	 * <p><b>Analogy:</b> the clerk writes up a complaint slip, stamps it with a reference number, and
	 * hands you the copy — the paper trail exists even though the warehouse hasn't moved yet.</p>
	 *
	 * @param orderId the order the claim is about (e.g. {@code "ORD-004"})
	 * @param reason  a short human reason (e.g. "invoice €240 higher than order total")
	 * @return a confirmation with a claim reference, or a clear message if the order is unknown
	 */
	@EasyTool("Files a claim (reklamacija) against an order for a stated reason; use when an invoice does not match the order")
	public String fileClaim(String orderId, String reason) {
		return catalog.find(orderId)
				.map(o -> {
					String claimId = "CLM-" + (Math.abs((orderId + String.valueOf(reason)).hashCode()) % 9000 + 1000);
					log.info("fileClaim: order={} reason={} -> {}", orderId, reason, claimId);
					return String.format("Reklamacija %s otvorena za nalog %s (kupac %s, total %.2f). Razlog: %s",
							claimId, o.getId(), o.getCustomer(), o.getTotal(),
							(reason == null || reason.isBlank() ? "neslaganje iznosa fakture i naloga" : reason));
				})
				.orElse("Ne mogu da otvorim reklamaciju — nalog " + orderId + " ne postoji.");
	}

	/**
	 * The rule: catch ALL the errors from the tool method, and allways return
	 * String - because only in that case AI knows how to interprets what's happen,
	 * and response in appropriate way!
	 * 
	 * I apologize, but it seems that "john_example.com" is not a valid email
	 * address. Could you please provide a valid email address so I can assist you
	 * with your orders?
	 * 
	 * @param email
	 * @return
	 */
	@EasyTool("Lists the orders for a customer, given their email address")
	public String getOrdersByCustomer(String email) {
		log.debug("getOrdersByCustomer(), email = {}", email);
		try {
			if (!isValidEmail(email)) {
				throw new RuntimeException("Please, specify one valid email address!");
			}
			List<String> orders = List.of("ORD-001", "ORD-002", "ORD-003");
			String result = String.join(", ", orders);
			return "Orders for email address " + email + " are: " + result;
			// return "Found " + orders.size() + " orders for " + email + ": " + result;
		} catch (Exception ex) {
			log.error("getOrdersByCustomer() failed for email={}", email, ex);
			return "Could not retrieve orders for " + email + ": " + ex.getMessage();
		}
	}

	private boolean isValidEmail(String email) {
		if (email == null || email.isEmpty()) {
			return false;
		}

		int atIndex = email.indexOf('@');
		if (atIndex <= 0 || atIndex == email.length() - 1) {
			return false;
		}

		int dotIndex = email.lastIndexOf('.', email.length() - 1);
		if (dotIndex <= atIndex + 1 || dotIndex == email.length() - 1) {
			return false;
		}
		return true;
	}

	@EasyTool("Creates an order for an item, given a reservation reference and a payment reference")
	public String createOrder(String item, String reservationRef, String paymentRef) {
		String orderId = "ORD-" + (Math.abs((reservationRef + paymentRef).hashCode()) % 9000 + 1000);
		return "Order created for " + item + ". Reservation: " + reservationRef + ", Payment: " + paymentRef
				+ ". Order ID: " + orderId;
	}
}

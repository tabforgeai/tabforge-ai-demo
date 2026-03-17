package services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ejb.Stateless;

@Stateless
public class OrderService {
	private static final Logger log = LoggerFactory.getLogger(OrderService.class);

	public String findOrder(String orderId) {
		return "Order " + orderId + ": shipped, arrives tomorrow";
	}

	public String cancelOrder(String orderId) {
		return "Order " + orderId + " has been cancelled";
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

	public String createOrder(String item, String reservationRef, String paymentRef) {
		String orderId = "ORD-" + (Math.abs((reservationRef + paymentRef).hashCode()) % 9000 + 1000);
		return "Order created for " + item + ". Reservation: " + reservationRef + ", Payment: " + paymentRef
				+ ". Order ID: " + orderId;
	}
}

package services;

import jakarta.ejb.Stateless;

@Stateless
public class ShippingService {

	public String scheduleDelivery(String orderId, String address) {
		return "Delivery scheduled for order " + orderId + " to " + address + ". Estimated arrival: 2-3 business days.";
	}

}

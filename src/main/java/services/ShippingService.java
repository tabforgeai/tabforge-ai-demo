package services;

import dyntabs.ai.annotation.EasyTool;
import jakarta.ejb.Stateless;

@Stateless
public class ShippingService {

	@EasyTool("Schedules delivery of an order to an address and returns the estimated arrival")
	public String scheduleDelivery(String orderId, String address) {
		return "Delivery scheduled for order " + orderId + " to " + address + ". Estimated arrival: 2-3 business days.";
	}

}

package services;

import dyntabs.ai.annotation.EasyTool;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PaymentService {

	@EasyTool("Applies the loyalty credit available for a user and returns the credited amount")
	public String applyLoyaltyCredit(String userCode) {
		return "150.0";
	}

	@EasyTool("Processes a payment of the given amount via the given payment method and returns a payment ID")
	public String processPayment(double amount, String paymentMethod) {
		String paymentId = "PAY-" + (Math.abs(paymentMethod.hashCode()) % 90000 + 10000);
		return "Payment of " + amount + " processed via " + paymentMethod + ". Payment ID: " + paymentId;
	}

}

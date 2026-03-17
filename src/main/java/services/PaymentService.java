package services;

import jakarta.ejb.Stateless;

@Stateless
public class PaymentService {
	public String applyLoyaltyCredit(String userCode) {
		return "150.0";
	}

	public String processPayment(double amount, String paymentMethod) {
		String paymentId = "PAY-" + (Math.abs(paymentMethod.hashCode()) % 90000 + 10000);
		return "Payment of " + amount + " processed via " + paymentMethod + ". Payment ID: " + paymentId;
	}

}

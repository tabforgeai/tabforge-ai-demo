package services;

import dyntabs.ai.annotation.EasyTool;

public class PricingService {
	@EasyTool("Returns the price of a product by name")
	public String getCostOfProduct(String prodName) {
		return "Cost of products  " + prodName + " is $150";
	}

}

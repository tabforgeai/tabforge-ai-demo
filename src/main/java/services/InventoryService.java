package services;

import dyntabs.ai.annotation.EasyTool;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class InventoryService {

	@EasyTool("Checks whether the requested quantity of a product is in stock")
	public boolean checkStock(String prodName, int quantity) {
		return quantity < 5;
	}

	@EasyTool("Reserves a quantity of a product from an alternate warehouse and returns a reservation reference")
	public String getFromWarehouse(String prodName, int quantity, String alternateWarehouse) {
		String ref = "REF-" + (Math.abs(prodName.hashCode()) % 9000 + 1000);
		return quantity + "x " + prodName + " reserved from warehouse " + alternateWarehouse
				+ ". Reservation reference: " + ref;
	}
}

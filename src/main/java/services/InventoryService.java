package services;

import jakarta.ejb.Stateless;

@Stateless
public class InventoryService {

	public boolean checkStock(String prodName, int quantity) {
		return quantity < 5;
	}

	public String getFromWarehouse(String prodName, int quantity, String alternateWarehouse) {
		String ref = "REF-" + (Math.abs(prodName.hashCode()) % 9000 + 1000);
		return quantity + "x " + prodName + " reserved from warehouse " + alternateWarehouse
				+ ". Reservation reference: " + ref;
	}
}

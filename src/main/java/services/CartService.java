package services;

import java.util.ArrayList;
import java.util.List;

import jakarta.ejb.Stateful;

@Stateful
public class CartService {

	private final List<String> items = new ArrayList<>();

	public String addItem(String itemName) {
		items.add(itemName);
		return "Added " + itemName + ". Cart has " + items.size() + " items.";
	}

	public String getCartContents() {
		return "Cart: " + String.join(", ", items);
	}
}
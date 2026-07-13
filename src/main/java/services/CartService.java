package services;

import java.util.ArrayList;
import java.util.List;

import dyntabs.ai.annotation.EasyTool;
import jakarta.ejb.Stateful;

@Stateful
public class CartService {

	private final List<String> items = new ArrayList<>();

	@EasyTool("Adds an item to the shopping cart")
	public String addItem(String itemName) {
		items.add(itemName);
		return "Added " + itemName + ". Cart has " + items.size() + " items.";
	}

	@EasyTool("Returns the current contents of the shopping cart")
	public String getCartContents() {
		return "Cart: " + String.join(", ", items);
	}
}

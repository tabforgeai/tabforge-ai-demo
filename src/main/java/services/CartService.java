package services;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import dyntabs.ai.annotation.EasyTool;
import jakarta.enterprise.context.SessionScoped;

/**
 * A shopping cart holds per-user state (the {@link #items} list), so it is
 * {@code @SessionScoped} — the idiomatic CDI way to do what a {@code @Stateful} EJB used
 * to do. One instance per HTTP session, cleaned up when the session ends; being session
 * state, it is {@link Serializable}.
 */
@SessionScoped
public class CartService implements Serializable {

	private static final long serialVersionUID = 1L;

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

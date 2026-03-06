package interfaces;

import dyntabs.ai.annotation.EasyAIAssistant;
import services.CartService;
import services.OrderService;
import services.UserService;

@EasyAIAssistant(systemMessage = "You are an order service and user service support bot. Help customers with their orders, and to find and delete users."
		+ "Also, help users on shopping, to add item to the shopping cart, and to list cart contents.", tools = {
				OrderService.class, UserService.class, CartService.class })
public interface SupportBot {
	String ask(String question);
}
package interfaces;

import dyntabs.ai.annotation.EasyAIAssistant;

@EasyAIAssistant(systemMessage = "You are a sales assistant. "
		+ "Answer question based on provided document content, and use pricing service to get cost of product.")
public interface SalesBot {
	String ask(String question);
}

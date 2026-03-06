package interfaces;

import dyntabs.ai.annotation.EasyAIAssistant;

@EasyAIAssistant(systemMessage = "Answer question based on provided document content")
public interface PolicyBot {
	String ask(String question);
}
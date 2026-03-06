package interfaces;

import dyntabs.ai.annotation.EasyAIAssistant;

@EasyAIAssistant(systemMessage = "You are a helpful code reviewer")
public interface CodeReviewer {
	String review(String code);
}

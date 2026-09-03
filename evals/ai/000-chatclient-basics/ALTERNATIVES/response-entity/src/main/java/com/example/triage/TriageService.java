package com.example.triage;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class TriageService {

	private final ChatClient chatClient;

	private final Resource systemPrompt;

	private final Resource userTemplate;

	public TriageService(ChatClient.Builder chatClientBuilder,
			@Value("classpath:/prompts/triage-system.st") Resource systemPrompt,
			@Value("classpath:/prompts/triage-user.st") Resource userTemplate) {
		this.chatClient = chatClientBuilder.build();
		this.systemPrompt = systemPrompt;
		this.userTemplate = userTemplate;
	}

	public TicketTriage triage(String ticketText) {
		ResponseEntity<ChatResponse, TicketTriage> result = this.chatClient.prompt()
				.system(system -> system.text(this.systemPrompt))
				.user(user -> user.text(this.userTemplate).param("ticket", ticketText))
				.call()
				.responseEntity(TicketTriage.class);
		return result.entity();
	}

}

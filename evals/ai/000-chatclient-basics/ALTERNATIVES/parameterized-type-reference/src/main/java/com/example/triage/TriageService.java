package com.example.triage;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class TriageService {

	private final ChatClient chatClient;

	private final Resource userTemplate;

	public TriageService(ChatClient.Builder chatClientBuilder,
			@Value("classpath:/prompts/triage-system.st") Resource systemPrompt,
			@Value("classpath:/prompts/triage-user.st") Resource userTemplate) {
		this.chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();
		this.userTemplate = userTemplate;
	}

	public TicketTriage triage(String ticketText) {
		return this.chatClient.prompt()
				.user(user -> user.text(this.userTemplate).params(Map.of("ticket", ticketText)))
				.call()
				.entity(new ParameterizedTypeReference<TicketTriage>() {
				});
	}

}

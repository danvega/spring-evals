package com.example.triage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class TriageService {

	private final ChatClient chatClient;

	private final BeanOutputConverter<TicketTriage> converter = new BeanOutputConverter<>(TicketTriage.class);

	private final Resource systemPrompt;

	private final String userTemplate;

	public TriageService(ChatClient.Builder chatClientBuilder,
			@Value("classpath:/prompts/triage-system.st") Resource systemPrompt,
			@Value("classpath:/prompts/triage-user.st") Resource userTemplate) throws IOException {
		this.chatClient = chatClientBuilder.build();
		this.systemPrompt = systemPrompt;
		this.userTemplate = userTemplate.getContentAsString(StandardCharsets.UTF_8) + "\n{format}";
	}

	public TicketTriage triage(String ticketText) {
		String content = this.chatClient.prompt()
				.system(this.systemPrompt)
				.user(user -> user.text(this.userTemplate)
						.param("ticket", ticketText)
						.param("format", this.converter.getFormat()))
				.call()
				.content();
		return this.converter.convert(content);
	}

}

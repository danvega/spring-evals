package com.example.triage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TriageService {

	private static final String REPLY_SHAPE = """
			Answer with a single JSON object and nothing else. It must match this JSON Schema:
			{"type":"object","properties":{"category":{"type":"string","enum":["BILLING","TECHNICAL","ACCOUNT","GENERAL"]},\
			"priority":{"type":"string","enum":["LOW","NORMAL","HIGH","URGENT"]},"summary":{"type":"string"}},\
			"required":["category","priority","summary"]}
			""";

	private final ChatClient chatClient;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

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
						.param("format", REPLY_SHAPE))
				.call()
				.content();
		return this.jsonMapper.readValue(content, TicketTriage.class);
	}

}

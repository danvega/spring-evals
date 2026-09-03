package com.example.triage;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class TriageService {

	private static final String REPLY_SHAPE = """
			Answer with a single JSON object and nothing else. It must match this JSON Schema:
			{"type":"object","properties":{"category":{"type":"string","enum":["BILLING","TECHNICAL","ACCOUNT","GENERAL"]},\
			"priority":{"type":"string","enum":["LOW","NORMAL","HIGH","URGENT"]},"summary":{"type":"string"}},\
			"required":["category","priority","summary"]}
			""";

	private static final Pattern CATEGORY = Pattern.compile("\"category\"\\s*:\\s*\"([A-Z]+)\"");

	private static final Pattern PRIORITY = Pattern.compile("\"priority\"\\s*:\\s*\"([A-Z]+)\"");

	private static final Pattern SUMMARY = Pattern.compile("\"summary\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

	private final ChatModel chatModel;

	private final SystemPromptTemplate systemTemplate;

	private final PromptTemplate userTemplate;

	public TriageService(ChatModel chatModel,
			@Value("classpath:/prompts/triage-system.st") Resource systemPrompt,
			@Value("classpath:/prompts/triage-user.st") Resource userPrompt) {
		this.chatModel = chatModel;
		this.systemTemplate = new SystemPromptTemplate(systemPrompt);
		this.userTemplate = new PromptTemplate(userPrompt);
	}

	public TicketTriage triage(String ticketText) {
		Message system = this.systemTemplate.createMessage();
		String userText = this.userTemplate.render(Map.of("ticket", ticketText)) + "\n" + REPLY_SHAPE;
		Prompt prompt = new Prompt(List.of(system, new UserMessage(userText)));
		String reply = this.chatModel.call(prompt).getResult().getOutput().getText();
		return new TicketTriage(TicketCategory.valueOf(group(CATEGORY, reply)),
				TicketPriority.valueOf(group(PRIORITY, reply)),
				group(SUMMARY, reply).replace("\\\"", "\""));
	}

	private static String group(Pattern pattern, String text) {
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find()) {
			throw new IllegalStateException("reply did not contain " + pattern.pattern());
		}
		return matcher.group(1);
	}

}

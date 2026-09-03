package com.example.triage;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class TriageService {

	private final ChatModel chatModel;

	private final BeanOutputConverter<TicketTriage> converter = new BeanOutputConverter<>(TicketTriage.class);

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
		String userText = this.userTemplate.render(Map.of("ticket", ticketText))
				+ "\n" + this.converter.getFormat();
		Prompt prompt = new Prompt(List.of(system, new UserMessage(userText)));
		String reply = this.chatModel.call(prompt).getResult().getOutput().getText();
		return this.converter.convert(reply);
	}

}

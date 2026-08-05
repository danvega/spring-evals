package com.example.triage;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
public class TriageService {

	private final ChatModel chatModel;

	public TriageService(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	public TicketTriage triage(String ticketText) {
		String prompt = "You are a helpdesk triage bot. "
				+ "Pick a category (BILLING, TECHNICAL, ACCOUNT, GENERAL) "
				+ "and a priority (LOW, NORMAL, HIGH, URGENT) for the ticket below. "
				+ "Reply on one line, exactly like this: "
				+ "Category: <category> | Priority: <priority> | Summary: <one sentence>. "
				+ "Ticket: " + ticketText;
		String reply = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
		String[] parts = reply.split("\\|");
		TicketCategory category = TicketCategory.valueOf(value(parts[0]));
		TicketPriority priority = TicketPriority.valueOf(value(parts[1]));
		return new TicketTriage(category, priority, value(parts[2]));
	}

	private String value(String part) {
		return part.substring(part.indexOf(':') + 1).trim();
	}

}

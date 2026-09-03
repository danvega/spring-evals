package com.example.triage.stub;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Offline stand-in for a real chat model so the app runs without provider
 * credentials. When the incoming user text carries JSON Schema format
 * instructions (the marker literals below match the text our pinned
 * spring-ai.version puts into the user message), it applies the team triage
 * rules to the ticket text and answers with matching JSON. Any other request
 * gets the same free-form acknowledgement the real model kept drifting into,
 * so no parser of that prose can recover triage data.
 */
public class StubChatModel implements ChatModel {

	private static final String FORMAT_MARKER = "Your response should be in JSON format";

	private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();

	public Prompt lastPrompt() {
		return lastPrompt.get();
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		lastPrompt.set(prompt);
		String userText = prompt.getInstructions().stream()
				.filter(message -> message.getMessageType() == MessageType.USER)
				.map(Message::getText)
				.filter(Objects::nonNull)
				.collect(Collectors.joining("\n"));
		boolean structured = userText.contains("JSON Schema");
		if (!structured) {
			return reply("Thanks for getting in touch. We have received the ticket and a support "
					+ "engineer will take a look shortly. Is there anything else you can share?");
		}
		int marker = userText.indexOf(FORMAT_MARKER);
		String ticketPart = marker >= 0 ? userText.substring(0, marker) : userText;
		String lower = ticketPart.toLowerCase(Locale.ROOT);
		return reply("{\"category\":\"%s\",\"priority\":\"%s\",\"summary\":\"%s\"}"
				.formatted(category(lower), priority(lower), escape(condense(ticketPart))));
	}

	private ChatResponse reply(String text) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
	}

	private String category(String text) {
		if (containsAny(text, "error", "crash", "down", "outage", "bug")) {
			return "TECHNICAL";
		}
		if (containsAny(text, "refund", "charge", "invoice", "payment")) {
			return "BILLING";
		}
		if (containsAny(text, "password", "login", "locked")) {
			return "ACCOUNT";
		}
		return "GENERAL";
	}

	private String priority(String text) {
		if (containsAny(text, "outage", "down", "production")) {
			return "URGENT";
		}
		if (containsAny(text, "cannot", "charged twice")) {
			return "HIGH";
		}
		if (text.contains("how do i")) {
			return "LOW";
		}
		return "NORMAL";
	}

	private boolean containsAny(String text, String... needles) {
		for (String needle : needles) {
			if (text.contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private String condense(String text) {
		String collapsed = text.replaceAll("\\s+", " ").trim();
		return collapsed.length() <= 400 ? collapsed : collapsed.substring(0, 400);
	}

	private String escape(String text) {
		return text.replace("\\", "\\\\").replace("\"", "\\\"")
				.replace("\n", " ").replace("\r", " ").replace("\t", " ");
	}

}

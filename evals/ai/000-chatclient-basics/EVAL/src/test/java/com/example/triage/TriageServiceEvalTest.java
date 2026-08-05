package com.example.triage;

import java.util.Objects;
import java.util.stream.Collectors;

import com.example.triage.stub.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "ai.stub=true")
class TriageServiceEvalTest {

	@Autowired
	TriageService triageService;

	@Autowired
	StubChatModel stubChatModel;

	@Test
	void billingTicketProducesTypedTriage() {
		TicketTriage triage = triageService.triage(
				"I was charged twice for my subscription this month. Please refund the extra invoice.");

		assertThat(triage.category())
				.as("a double-charge ticket must be categorized as BILLING")
				.isEqualTo(TicketCategory.BILLING);
		assertThat(triage.priority())
				.as("a double-charge ticket must be triaged as HIGH priority")
				.isEqualTo(TicketPriority.HIGH);
		assertThat(triage.summary())
				.as("the summary must reflect the customer's actual problem")
				.contains("charged twice");
	}

	@Test
	void accountQuestionIsLowPriority() {
		TicketTriage triage = triageService.triage(
				"How do I update the login email on my account profile?");

		assertThat(triage.category())
				.as("a sign-in detail question must be categorized as ACCOUNT")
				.isEqualTo(TicketCategory.ACCOUNT);
		assertThat(triage.priority())
				.as("a how-do-I question must be triaged as LOW priority")
				.isEqualTo(TicketPriority.LOW);
		assertThat(triage.summary())
				.as("the summary must reflect the customer's actual problem")
				.contains("login email");
	}

	@Test
	void ticketContainingDelimiterCharactersStillTriagesCleanly() {
		TicketTriage triage = triageService.triage(
				"Status page says OK | but our dashboard is down | error 500 | region: eu-west");

		assertThat(triage.category())
				.as("an outage-style ticket must be categorized as TECHNICAL even when "
						+ "the ticket text contains | and : characters")
				.isEqualTo(TicketCategory.TECHNICAL);
		assertThat(triage.priority())
				.as("a down-service ticket must be triaged as URGENT")
				.isEqualTo(TicketPriority.URGENT);
		assertThat(triage.summary())
				.as("the summary must survive delimiter characters in the ticket text")
				.contains("dashboard");
	}

	@Test
	void modelReceivesStandingRulesAndTheTicket() {
		triageService.triage("Reference VX-9931. The exporter keeps crashing when I click save.");

		Prompt prompt = stubChatModel.lastPrompt();
		assertThat(prompt)
				.as("triage must go through the chat model, not local parsing rules")
				.isNotNull();

		String systemText = textOf(prompt, MessageType.SYSTEM);
		assertThat(systemText)
				.as("the agreed triage rules must reach the model as standing system instructions")
				.contains("Skyline Software")
				.contains("Never invent order numbers");

		String userText = textOf(prompt, MessageType.USER);
		assertThat(userText)
				.as("the ticket text must reach the model in the per-request user message")
				.contains("VX-9931");
	}

	private String textOf(Prompt prompt, MessageType type) {
		return prompt.getInstructions().stream()
				.filter(message -> message.getMessageType() == type)
				.map(Message::getText)
				.filter(Objects::nonNull)
				.collect(Collectors.joining("\n"));
	}

}

package com.example.triage.stub;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StubModelConfig {

	@Bean
	@ConditionalOnProperty(name = "ai.stub", havingValue = "true")
	public StubChatModel stubChatModel() {
		return new StubChatModel();
	}

}

package com.example.userapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class UserapiApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserapiApplication.class, args);
	}

	@Bean
	WebMvcConfigurer apiVersioning() {
		return new WebMvcConfigurer() {
			@Override
			public void configureApiVersioning(ApiVersionConfigurer configurer) {
				configurer.useRequestHeader("X-API-Version").setDefaultVersion("1.0");
			}
		};
	}

}

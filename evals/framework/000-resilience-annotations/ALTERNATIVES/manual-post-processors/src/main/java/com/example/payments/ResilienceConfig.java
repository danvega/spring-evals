package com.example.payments;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.ConcurrencyLimitBeanPostProcessor;
import org.springframework.resilience.annotation.RetryAnnotationBeanPostProcessor;

/**
 * Registers the two resilience post-processors directly instead of going
 * through @EnableResilientMethods. Static so the container creates them
 * before the beans they advise.
 */
@Configuration(proxyBeanMethods = false)
public class ResilienceConfig {

    @Bean
    static RetryAnnotationBeanPostProcessor retryAnnotationBeanPostProcessor() {
        return new RetryAnnotationBeanPostProcessor();
    }

    @Bean
    static ConcurrencyLimitBeanPostProcessor concurrencyLimitBeanPostProcessor() {
        return new ConcurrencyLimitBeanPostProcessor();
    }
}

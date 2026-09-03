package com.example.payments;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
public class ResilienceConfig {
}

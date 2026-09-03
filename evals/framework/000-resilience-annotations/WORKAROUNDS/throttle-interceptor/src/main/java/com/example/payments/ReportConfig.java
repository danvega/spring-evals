package com.example.payments;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.interceptor.ConcurrencyThrottleInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
public class ReportConfig {

    @Bean
    ReportService reportService(ReportGenerator reportGenerator) {
        ProxyFactory proxy = new ProxyFactory(new ReportService(reportGenerator));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new ConcurrencyThrottleInterceptor(2));
        return (ReportService) proxy.getProxy();
    }
}

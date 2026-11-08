package com.example.demo.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({IdentityProperties.class, VerificationProperties.class})
public class IdentityConfiguration {
}

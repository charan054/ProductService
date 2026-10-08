package com.example.productservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {
    // One place for "now" so reports can be tested with a fixed clock.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

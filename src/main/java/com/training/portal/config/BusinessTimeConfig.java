package com.training.portal.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessTimeConfig {
    @Bean
    Clock businessClock() {
        return Clock.system(ZoneId.of("Asia/Tokyo"));
    }
}

package com.mockingbirdbank.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The bank's business clock. "Today" for date-based rules, such as the Youth Savings age check, is
 * the calendar date in {@link #BUSINESS_ZONE}, not the server's or UTC's date: at 9 p.m. Eastern it
 * is already tomorrow in UTC, but a customer whose 18th birthday is tomorrow is still 17.
 */
@Configuration
public class BusinessClockConfig {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/New_York");

    @Bean
    public Clock businessClock() {
        return Clock.system(BUSINESS_ZONE);
    }
}

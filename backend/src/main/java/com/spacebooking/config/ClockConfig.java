package com.spacebooking.config;

import com.spacebooking.testsupport.E2eClock;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class ClockConfig {

    @Bean
    @Profile("!e2e")
    Clock clock() {
        return Clock.systemUTC();
    }

    // Return type is the subclass so TestSupportService can inject it; every Clock consumer gets this same bean.
    @Bean
    @Profile("e2e")
    E2eClock e2eClock() {
        return new E2eClock();
    }
}

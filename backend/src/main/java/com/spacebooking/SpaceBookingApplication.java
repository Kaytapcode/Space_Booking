package com.spacebooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpaceBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpaceBookingApplication.class, args);
    }
}

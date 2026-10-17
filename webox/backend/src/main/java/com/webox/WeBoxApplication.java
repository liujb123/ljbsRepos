package com.webox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * WeBox - Enterprise Employee Meal Ordering Platform.
 *
 * <p>Target architecture: Vue SPA -> Spring Boot (JDK 17) -> standalone MySQL,
 * with Redis used strictly as a degradable accelerator (never the source of truth).
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded on purpose: authentication is
 * stateless JWT, so the default in-memory user (and its generated password) must not exist.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableTransactionManagement
public class WeBoxApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeBoxApplication.class, args);
    }
}

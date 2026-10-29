package com.campus.events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Campus Event Management System - Spring Boot Application Entry Point
 * Uses raw JDBC with DAO pattern (no JPA/Hibernate).
 */
@SpringBootApplication(exclude = {HibernateJpaAutoConfiguration.class})
@EnableScheduling
public class EventManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventManagementApplication.class, args);
    }
}

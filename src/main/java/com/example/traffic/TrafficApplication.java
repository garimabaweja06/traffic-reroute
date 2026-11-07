package com.example.traffic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the web server. Spring finds the other classes in this package automatically. */
@SpringBootApplication
public class TrafficApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrafficApplication.class, args);
    }
}

package com.oneshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OneShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(OneShopApplication.class, args);
    }
}

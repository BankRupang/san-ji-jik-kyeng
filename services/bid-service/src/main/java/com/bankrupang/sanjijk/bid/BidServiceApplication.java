package com.bankrupang.sanjijk.bid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class BidServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BidServiceApplication.class, args);
    }

}

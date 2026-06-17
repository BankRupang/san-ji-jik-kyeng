package com.bankrupang.sanjijk.auction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.cloud.config.enabled=false"
})
class AuctionServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}

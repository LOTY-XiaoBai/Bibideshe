package com.example.bishe;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.token.secret=test-token-secret-with-at-least-32-characters")
class BisheApplicationTests {

    @Test
    void contextLoads() {
    }

}

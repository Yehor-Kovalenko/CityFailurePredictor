package com.citydisruptors.auth_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "logging.config=classpath:logback-test.xml"
})
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}

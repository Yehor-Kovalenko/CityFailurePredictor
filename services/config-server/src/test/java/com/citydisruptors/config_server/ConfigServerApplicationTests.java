package com.citydisruptors.config_server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "native"})
class ConfigServerApplicationTests {

    @Test
    void contextLoads() {
    }

}

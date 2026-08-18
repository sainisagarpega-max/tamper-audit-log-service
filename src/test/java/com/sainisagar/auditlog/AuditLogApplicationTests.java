package com.sainisagar.auditlog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused")
class AuditLogApplicationTests {

    @Test
    void contextLoads() {
    }
}

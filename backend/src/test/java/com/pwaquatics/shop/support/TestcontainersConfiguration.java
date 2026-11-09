package com.pwaquatics.shop.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real PostgreSQL for integration tests (never H2). The container is shared by all
 * test classes that use the same Spring context.
 *
 * <p>Behind a Docker Hub rate limit, set {@code TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX=mirror.gcr.io/}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18-alpine");
    }
}

package com.saaketh.budget;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a throwaway Postgres in Docker for tests that need a real database.
 * {@code @ServiceConnection} points Spring's datasource at it automatically.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Same version as docker-compose.yml and production.
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    }
}

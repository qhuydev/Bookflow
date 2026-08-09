package com.bookflow.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class RedisTestcontainerConfiguration {
    public static final String TEST_PASSWORD = "bf021-testcontainer-only";

    @Bean(destroyMethod = "stop")
    GenericContainer<?> redisContainer() {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("redis:8.6.5-alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server", "--save", "", "--appendonly", "no", "--requirepass", TEST_PASSWORD);
        container.start();
        return container;
    }

    @Bean
    LettuceConnectionFactory redisConnectionFactory(GenericContainer<?> redisContainer) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                redisContainer.getHost(),
                redisContainer.getMappedPort(6379)
        );
        configuration.setPassword(RedisPassword.of(TEST_PASSWORD));
        return new LettuceConnectionFactory(configuration);
    }
}

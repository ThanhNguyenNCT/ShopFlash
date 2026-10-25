package com.shopflash.modules.transaction.fixture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import redis.clients.jedis.RedisClient;

/** Gọi đích danh với -Dtx.fixture.action=setup|reset|cleanup và -Dtx.fixture.id=<id>. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.flyway.enabled=false")
class TransactionFixtureCommandIT {
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired RedisClient redis;

    @Test
    void runRequestedAction() {
        String id = System.getProperty("tx.fixture.id");
        String action = System.getProperty("tx.fixture.action");
        if (id == null || action == null) {
            throw new IllegalArgumentException("Cần -Dtx.fixture.id và -Dtx.fixture.action");
        }
        TransactionFixtures fixtures = new TransactionFixtures(jdbc, manager, redis);
        switch (action) {
            case "setup" -> fixtures.setup(id);
            case "reset" -> fixtures.reset(id);
            case "cleanup" -> fixtures.cleanup(id);
            default -> throw new IllegalArgumentException("Action phải là setup, reset hoặc cleanup");
        }
    }
}

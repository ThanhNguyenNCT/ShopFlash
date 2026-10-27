package com.shopflash.modules.transaction.study;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.shopflash.modules.transaction.fixture.TransactionFixtures;

import redis.clients.jedis.RedisClient;

/** Lệnh setup/reset/cleanup nhiều khách HTTP; manifest chỉ chứa email và ID sản phẩm. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.flyway.enabled=false")
class TransactionHttpFixtureCommandIT {
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired RedisClient redis;

    @Test
    void runRequestedAction() throws Exception {
        String action = System.getProperty("tx.fixture.action", "");
        String rawIds = System.getProperty("tx.fixture.ids", "");
        if (rawIds.isBlank()) throw new IllegalArgumentException("Cần -Dtx.fixture.ids=id1,id2,...");
        List<String> ids = List.of(rawIds.split(","));
        if (ids.size() < 10 || ids.size() > 20 || ids.stream().anyMatch(String::isBlank)
                || ids.stream().distinct().count() != ids.size()) {
            throw new IllegalArgumentException("Cần 10–20 ID fixture khác nhau");
        }
        TransactionFixtures fixtures = new TransactionFixtures(jdbc, manager, redis);
        for (String id : ids) {
            switch (action) {
                case "setup" -> fixtures.setup(id);
                case "reset" -> fixtures.reset(id);
                case "cleanup" -> fixtures.cleanup(id);
                default -> throw new IllegalArgumentException("Action phải là setup, reset hoặc cleanup");
            }
        }
        if (!action.equals("cleanup")) {
            Path manifest = Path.of(System.getProperty("tx.fixture.manifest", "target/transaction-http-fixtures.json"));
            Files.createDirectories(manifest.toAbsolutePath().getParent());
            List<String> entries = new ArrayList<>();
            for (String id : ids) {
                List<String> products = new ArrayList<>();
                for (int number = 1; number <= 5; number++) {
                    long productId = jdbc.queryForObject("SELECT product_id FROM products WHERE sku = :sku",
                            Map.of("sku", "TXLAB-" + id.toUpperCase() + "-" + number), Long.class);
                    products.add(Long.toString(productId));
                }
                entries.add("{\"id\":\"" + id + "\",\"email\":\"txlab-" + id
                        + "@shopflash.local\",\"productIds\":[" + String.join(",", products) + "]}");
            }
            Files.writeString(manifest, "[" + String.join(",", entries) + "]\n", StandardCharsets.UTF_8);
        }
    }
}

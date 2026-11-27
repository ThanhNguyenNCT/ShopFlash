package com.shopflash.modules.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.reset;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.storage.dto.response.ProjectionStatusResponse;
import com.shopflash.modules.storage.dto.response.ProjectionStatusResponse.Issue;
import com.shopflash.modules.storage.repository.ProductProjectionRepository;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import redis.clients.jedis.RedisClient;

/**
 * Oracle/Redis thật của môi trường test. Mỗi test dựng lại projection trước, sửa vài document Redis có chủ đích,
 * rồi dựng lại lần nữa khi kết thúc. Không sửa dữ liệu Oracle.
 */
@SpringBootTest
class ProjectionConsistencyIT {

    private static final String EXTRA_KEY = "product:999999999";
    private static final String SECRET = Base64.getEncoder().encodeToString("projection-test-secret-32-byte-key!".getBytes());

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET_BASE64", () -> SECRET);
    }

    @Autowired StorageService storage;
    @Autowired ProductProjectionSync sync;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired RedisClient redis;
    @MockitoSpyBean ProductProjectionRepository projection;

    private List<Long> ids;

    @BeforeEach
    void inSyncBaseline() {
        storage.rebuildProjection();
        ids = jdbc.queryForList("""
                SELECT p.product_id FROM products p JOIN product_inventory i ON i.product_id = p.product_id
                ORDER BY p.product_id FETCH FIRST 4 ROWS ONLY
                """, Map.of(), Long.class);
        assertThat(ids).as("môi trường test cần ít nhất 4 sản phẩm").hasSize(4);
        ProjectionStatusResponse baseline = storage.projectionStatus();
        assertThat(baseline.checkState()).isEqualTo(ProjectionStatusResponse.COMPLETED);
        assertThat(baseline.inSync()).isTrue();
        assertThat(baseline.matched()).isEqualTo(baseline.redisDocuments());
    }

    @AfterEach
    void restore() {
        reset(projection);
        storage.rebuildProjection();
    }

    @Test
    void sameCountButOneWrongFieldIsMismatch() {
        redis.sendCommand(() -> "JSON.SET".getBytes(), key(0), "$.price", "1");
        ProjectionStatusResponse status = storage.projectionStatus();

        assertThat(status.redisDocuments()).isEqualTo(status.matched() + 1);
        assertThat(status.mismatched()).isEqualTo(1);
        assertThat(status.missing() + status.extra() + status.invalid()).isZero();
        assertThat(status.inSync()).isFalse();
        Issue issue = status.issues().getFirst();
        assertThat(issue.kind()).isEqualTo("MISMATCH");
        assertThat(issue.productId()).isEqualTo(ids.get(0));
        assertThat(issue.fields()).singleElement().satisfies(d -> assertThat(d.field()).isEqualTo("price"));
    }

    @Test
    void detectsMissingExtraAndInvalidThenRebuildFixesThem() {
        redis.unlink(key(0));                                                    // thiếu
        redis.sendCommand(() -> "JSON.SET".getBytes(), EXTRA_KEY, "$", redisDocText(999999999));  // thừa
        redis.unlink(key(1));
        redis.set(key(1), "không phải JSON");                                    // sai kiểu key
        redis.sendCommand(() -> "JSON.SET".getBytes(), key(2), "$.inStock", "\"yes\"");  // sai kiểu field

        ProjectionStatusResponse status = storage.projectionStatus();
        assertThat(status.missing()).isEqualTo(1);
        assertThat(status.extra()).isEqualTo(1);
        assertThat(status.invalid()).isEqualTo(2);
        assertThat(status.mismatched()).isZero();
        assertThat(status.inSync()).isFalse();
        assertThat(status.issues()).extracting(Issue::kind, Issue::productId).contains(
                tuple("MISSING", ids.get(0)),
                tuple("EXTRA", 999999999L),
                tuple("INVALID", ids.get(1)),
                tuple("INVALID", ids.get(2)));

        storage.rebuildProjection();
        ProjectionStatusResponse after = storage.projectionStatus();
        assertThat(after.inSync()).isTrue();
        assertThat(after.missing() + after.extra() + after.mismatched() + after.invalid()).isZero();
        assertThat(after.matched()).isEqualTo(after.redisDocuments());
    }

    @Test
    void writeThatRacesWithANewerCommitIsRewrittenFromOracle() {
        long id = ids.get(3);
        Long onHand = jdbc.queryForObject("SELECT on_hand_quantity FROM product_inventory WHERE product_id = :id",
                Map.of("id", id), Long.class);
        String zero = "UPDATE product_inventory SET on_hand_quantity = reserved_flash_quantity, version = version + 1 WHERE product_id = :id";
        try {
            // Giả lập checkout khác commit đúng lúc bản đọc đầu tiên đang được ghi: bản ghi đó đã cũ.
            AtomicBoolean first = new AtomicBoolean(true);
            doAnswer(call -> {
                Object result = call.callRealMethod();
                if (first.getAndSet(false)) jdbc.update(zero, Map.of("id", id));
                return result;
            }).when(projection).upsertAll(anyList());

            clearInvocations(projection);
            sync.sync(List.of(id));

            verify(projection, times(2)).upsertAll(anyList());
            ProjectionStatusResponse status = storage.projectionStatus();
            assertThat(status.inSync()).isTrue();
        } finally {
            jdbc.update("UPDATE product_inventory SET on_hand_quantity = :q WHERE product_id = :id",
                    Map.of("q", onHand, "id", id));
        }
    }

    @Test
    void redisUnavailableMeansUnknownNotInSync() {
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REDIS_UNAVAILABLE", "Không kết nối được Redis"))
                .when(projection).keys();
        ProjectionStatusResponse status = storage.projectionStatus();

        assertThat(status.checkState()).isEqualTo(ProjectionStatusResponse.FAILED);
        assertThat(status.failureCode()).isEqualTo("REDIS_UNAVAILABLE");
        assertThat(status.inSync()).isFalse();
        assertThat(status.matched()).isNull();
    }

    private String key(int index) {
        return ProductProjectionRepository.key(ids.get(index));
    }

    private static String redisDocText(long id) {
        return "{\"id\":" + id + ",\"sku\":\"X\",\"name\":\"X\",\"categoryId\":1,\"categoryName\":\"X\",\"price\":1,"
                + "\"status\":\"ACTIVE\",\"inStock\":true,\"thumbnailUrl\":null}";
    }
}

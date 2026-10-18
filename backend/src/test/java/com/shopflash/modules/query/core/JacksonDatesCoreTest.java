package com.shopflash.modules.query.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopflash.modules.query.dto.response.ReportMeta;
import com.shopflash.modules.query.dto.response.StatsResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Mặc định của Jackson 3: OffsetDateTime là chuỗi ISO-8601 giữ offset +07:00, BigDecimal là số JSON. */
class JacksonDatesCoreTest {

    @Test
    void offsetDateTimeIsIsoStringAndMoneyIsNumber() {
        JsonMapper mapper = JsonMapper.builder().build();
        OffsetDateTime at = OffsetDateTime.parse("2026-10-18T15:04:05+07:00");

        String meta = mapper.writeValueAsString(new ReportMeta("oracle", "top-products", "HASH", true, 12.84, 1,
                "SELECT", 10, at, null));
        assertThat(meta).contains("\"generatedAt\":\"2026-10-18T15:04:05+07:00\"")
                .contains("\"elapsedMs\":12.84")
                .contains("\"note\":null");

        String stats = mapper.writeValueAsString(new StatsResponse("GATHER",
                List.of(new StatsResponse.TableStats("ORDERS", 100000L, at)), 3120.2));
        assertThat(stats).contains("\"lastAnalyzed\":\"2026-10-18T15:04:05+07:00\"");

        assertThat(mapper.writeValueAsString(new BigDecimal("98000000.00"))).isEqualTo("98000000.00");
    }
}

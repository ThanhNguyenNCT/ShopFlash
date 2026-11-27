package com.shopflash.modules.storage.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shopflash.modules.storage.dto.response.ProjectionStatusResponse.FieldDiff;
import com.shopflash.modules.storage.model.CatalogProduct;
import com.shopflash.modules.storage.model.CategoryStatus;
import com.shopflash.modules.storage.model.Product;
import com.shopflash.modules.storage.model.ProductInventory;
import com.shopflash.modules.storage.model.ProductStatus;
import com.shopflash.modules.storage.repository.ProductProjectionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ProjectionConsistencyCoreTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String GOOD = """
            {"id":7,"sku":"SKU-7","name":"Áo \\"thun\\"","categoryId":3,"categoryName":"Thời trang",
             "price":1000,"status":"ACTIVE","inStock":true,"thumbnailUrl":null}
            """;

    private static CatalogProduct product(long onHand, long reserved) {
        Product p = new Product(7, 3, "SKU-7", "Áo \"thun\"", "ao-thun", null, new BigDecimal("1000.00"), null,
                ProductStatus.ACTIVE, null, null);
        return new CatalogProduct(p, "Thời trang", CategoryStatus.ACTIVE, new ProductInventory(7, onHand, reserved, 0, null));
    }

    private static JsonNode node(String json) {
        return JSON.readTree(json);
    }

    @Test
    void matchesByValueNotByJsonText() {
        // Giá 1000 trong JSON và 1000.00 từ Oracle là cùng giá trị; thứ tự field không quan trọng.
        Map<String, Object> expected = ProductProjectionRepository.fields(product(5, 0));
        assertThat(ProjectionConsistencyChecker.structureProblem(node(GOOD))).isNull();
        assertThat(ProjectionConsistencyChecker.diff(expected, node(GOOD))).isEmpty();
    }

    @Test
    void reportsEachDifferentFieldWithBothValues() {
        // Còn 5 nhưng đã giữ hết cho flash sale: Oracle kỳ vọng inStock = false.
        Map<String, Object> expected = ProductProjectionRepository.fields(product(5, 5));
        JsonNode stale = node(GOOD.replace("\"price\":1000", "\"price\":999"));
        List<FieldDiff> diffs = ProjectionConsistencyChecker.diff(expected, stale);
        assertThat(diffs).extracting(FieldDiff::field).containsExactly("price", "inStock");
        assertThat(diffs.get(1).oracle()).isEqualTo(false);
    }

    @Test
    void wrongShapeIsInvalidNotMismatch() {
        assertThat(ProjectionConsistencyChecker.structureProblem(node("[1,2]"))).contains("JSON object");
        assertThat(ProjectionConsistencyChecker.structureProblem(node(GOOD.replace("\"price\":1000", "\"price\":\"1000\""))))
                .contains("price");
        assertThat(ProjectionConsistencyChecker.structureProblem(node(GOOD.replace(",\"thumbnailUrl\":null", ""))))
                .contains("Thiếu field thumbnailUrl");
        assertThat(ProjectionConsistencyChecker.structureProblem(node(GOOD.replace("\"id\":7", "\"id\":7,\"x\":1"))))
                .contains("ngoài schema: x");
        assertThat(ProjectionConsistencyChecker.structureProblem(node(GOOD.replace("\"id\":7", "\"id\":7.5"))))
                .contains("id");
    }

    @Test
    void onlyNumericProductKeysHaveAnId() {
        assertThat(ProjectionConsistencyChecker.idOf("product:42")).isEqualTo(42L);
        assertThat(ProjectionConsistencyChecker.idOf("product:abc")).isNull();
        assertThat(ProjectionConsistencyChecker.idOf("product:")).isNull();
        assertThat(ProjectionConsistencyChecker.idOf("other:42")).isNull();
    }
}

package com.shopflash.modules.concurrency.repository.oracle;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.shopflash.modules.concurrency.model.ItemRuntime;
import com.shopflash.modules.concurrency.model.SaleRuntime;

/** Đọc từ Oracle những gì cần để nạp hoặc dựng lại runtime flash sale trên Redis. */
@Repository
public class FlashRuntimeQueries {

    private final NamedParameterJdbcTemplate jdbc;

    public FlashRuntimeQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SaleRuntime> sale(long saleId) {
        List<SaleRuntime> rows = jdbc.query("""
                SELECT fs.flash_sale_id, fs.status, fs.start_at, fs.end_at,
                       c.max_quantity_per_user, c.reservation_timeout_seconds
                FROM   flash_sales fs
                JOIN   flash_sale_configs c ON c.flash_sale_id = fs.flash_sale_id
                WHERE  fs.flash_sale_id = :saleId
                """, Map.of("saleId", saleId), (rs, n) -> new SaleRuntime(
                rs.getLong("flash_sale_id"), rs.getString("status"),
                epochMs(rs.getObject("start_at", OffsetDateTime.class)),
                epochMs(rs.getObject("end_at", OffsetDateTime.class)),
                rs.getInt("max_quantity_per_user"), rs.getInt("reservation_timeout_seconds")));
        return rows.stream().findFirst();
    }

    /**
     * Sản phẩm của chương trình kèm số lượng mỗi khách đã mua (đơn chưa hủy), trong một câu lệnh để
     * sold_quantity và tổng theo khách cùng một thời điểm đọc nhất quán.
     */
    public List<ItemRuntime> items(long saleId) {
        record Row(long itemId, long productId, int allocated, int sold, String status, Long userId, Integer qty) {
        }
        List<Row> rows = jdbc.query("""
                SELECT fi.flash_sale_item_id, fi.product_id, fi.allocated_quantity, fi.sold_quantity, fi.status,
                       u.user_id, u.qty
                FROM   flash_sale_items fi
                LEFT JOIN (
                  SELECT oi.flash_sale_item_id, o.user_id, SUM(oi.quantity) AS qty
                  FROM   order_items oi
                  JOIN   orders o ON o.order_id = oi.order_id
                  WHERE  o.status <> 'CANCELLED'
                  AND    oi.flash_sale_item_id IN (
                           SELECT flash_sale_item_id FROM flash_sale_items WHERE flash_sale_id = :saleId)
                  GROUP BY oi.flash_sale_item_id, o.user_id
                ) u ON u.flash_sale_item_id = fi.flash_sale_item_id
                WHERE  fi.flash_sale_id = :saleId
                ORDER BY fi.flash_sale_item_id
                """, Map.of("saleId", saleId), (rs, n) -> new Row(
                rs.getLong("flash_sale_item_id"), rs.getLong("product_id"), rs.getInt("allocated_quantity"),
                rs.getInt("sold_quantity"), rs.getString("status"),
                rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                rs.getObject("qty") == null ? null : rs.getInt("qty")));

        Map<Long, Row> first = new LinkedHashMap<>();
        Map<Long, Map<Long, Integer>> bought = new LinkedHashMap<>();
        for (Row row : rows) {
            first.putIfAbsent(row.itemId(), row);
            Map<Long, Integer> users = bought.computeIfAbsent(row.itemId(), k -> new LinkedHashMap<>());
            if (row.userId() != null) {
                users.put(row.userId(), row.qty());
            }
        }
        List<ItemRuntime> items = new ArrayList<>(first.size());
        first.forEach((itemId, row) -> items.add(new ItemRuntime(itemId, row.productId(), row.allocated(), row.sold(),
                row.status(), bought.get(itemId))));
        return items;
    }

    public List<Long> itemIds(long saleId) {
        return jdbc.queryForList("SELECT flash_sale_item_id FROM flash_sale_items WHERE flash_sale_id = :saleId",
                Map.of("saleId", saleId), Long.class);
    }

    /** Chương trình cần có runtime trên Redis: đã lên lịch hoặc đang chạy. */
    public List<Long> runtimeSaleIds() {
        return jdbc.queryForList("""
                SELECT flash_sale_id FROM flash_sales
                WHERE  status IN ('SCHEDULED', 'ACTIVE')
                ORDER BY flash_sale_id
                """, Map.of(), Long.class);
    }

    private static long epochMs(OffsetDateTime time) {
        return time.toInstant().toEpochMilli();
    }
}

package com.shopflash.modules.query.repository.oracle;

import com.shopflash.modules.query.model.JoinHint;
import com.shopflash.modules.query.model.Report;

/**
 * Câu SQL của các báo cáo Oracle. Phần động duy nhất là hint, lấy từ chuỗi cố định theo JoinHint;
 * mọi giá trị khác đều là bind variable (:fromTs, :toExclusiveTs, :limit, :offset, :size).
 *
 * <p>Chỗ {@value #HINT} trong template được thay bằng comment hint của Oracle hoặc chuỗi rỗng.
 * Alias cố định o, oi, p, c, u để LEADING/USE_* trỏ đúng bảng.
 */
final class OracleReportSql {

    static final String HINT = "{{HINT}}";

    /** Đơn được tính và khoảng thời gian, dùng chung cho mọi báo cáo có from/to. */
    private static final String COUNTED_ORDERS = """
            o.status IN ('CONFIRMED', 'COMPLETED')
              AND o.created_at >= :fromTs
              AND o.created_at < :toExclusiveTs""";

    static final String REVENUE_BY_MONTH = """
            SELECT %sTO_CHAR(o.created_at AT TIME ZONE '+07:00', 'YYYY-MM') AS month,
                   COUNT(*) AS order_count,
                   SUM(o.total_amount) AS revenue
            FROM orders o
            WHERE %s
            GROUP BY TO_CHAR(o.created_at AT TIME ZONE '+07:00', 'YYYY-MM')
            ORDER BY month""".formatted(HINT, COUNTED_ORDERS);

    static final String REVENUE_BY_CATEGORY = """
            SELECT %sc.category_id,
                   c.name AS category_name,
                   SUM(oi.quantity) AS quantity,
                   SUM(oi.line_total) AS revenue
            FROM orders o
            JOIN order_items oi ON oi.order_id = o.order_id
            JOIN products p ON p.product_id = oi.product_id
            JOIN categories c ON c.category_id = p.category_id
            WHERE %s
            GROUP BY c.category_id, c.name
            ORDER BY revenue DESC, c.category_id""".formatted(HINT, COUNTED_ORDERS);

    static final String TOP_PRODUCTS = """
            SELECT %sp.product_id,
                   p.name AS product_name,
                   c.name AS category_name,
                   SUM(oi.quantity) AS quantity,
                   SUM(oi.line_total) AS revenue
            FROM orders o
            JOIN order_items oi ON oi.order_id = o.order_id
            JOIN products p ON p.product_id = oi.product_id
            JOIN categories c ON c.category_id = p.category_id
            WHERE %s
            GROUP BY p.product_id, p.name, c.name
            ORDER BY quantity DESC, p.product_id
            FETCH FIRST :limit ROWS ONLY""".formatted(HINT, COUNTED_ORDERS);

    /** Subquery trong HAVING: chỉ giữ khách chi tiêu trên mức trung bình của các khách có đơn trong kỳ. */
    static final String VIP_CUSTOMERS = """
            SELECT %su.user_id,
                   u.full_name,
                   u.email,
                   COUNT(*) AS order_count,
                   SUM(o.total_amount) AS total_spent
            FROM orders o
            JOIN users u ON u.user_id = o.user_id
            WHERE %s
            GROUP BY u.user_id, u.full_name, u.email
            HAVING SUM(o.total_amount) > (
                SELECT AVG(t.spent)
                FROM (SELECT SUM(o2.total_amount) AS spent
                      FROM orders o2
                      WHERE o2.status IN ('CONFIRMED', 'COMPLETED')
                        AND o2.created_at >= :fromTs
                        AND o2.created_at < :toExclusiveTs
                      GROUP BY o2.user_id) t)
            ORDER BY total_spent DESC, u.user_id
            OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY""".formatted(HINT, COUNTED_ORDERS);

    /** Câu thứ 2 của vip-customers: trung bình, số khách có đơn, số khách VIP (page.total). */
    static final String VIP_SUMMARY = """
            SELECT MAX(s.avg_spent) AS average_spent,
                   COUNT(*) AS customer_count,
                   COUNT(CASE WHEN s.spent > s.avg_spent THEN 1 END) AS vip_count
            FROM (SELECT SUM(o.total_amount) AS spent,
                         AVG(SUM(o.total_amount)) OVER () AS avg_spent
                  FROM orders o
                  WHERE %s
                  GROUP BY o.user_id) s""".formatted(COUNTED_ORDERS);

    static final String UNSOLD_PRODUCTS = """
            SELECT %sp.product_id,
                   p.name AS product_name,
                   c.name AS category_name,
                   p.base_price
            FROM products p
            JOIN categories c ON c.category_id = p.category_id
            WHERE p.status = 'ACTIVE'
              AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = p.product_id)
            ORDER BY p.product_id
            OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY""".formatted(HINT);

    static final String UNSOLD_COUNT = """
            SELECT COUNT(*)
            FROM products p
            WHERE p.status = 'ACTIVE'
              AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = p.product_id)""";

    private OracleReportSql() {
    }

    static String template(Report report) {
        return switch (report) {
            case REVENUE_BY_MONTH -> REVENUE_BY_MONTH;
            case REVENUE_BY_CATEGORY -> REVENUE_BY_CATEGORY;
            case TOP_PRODUCTS -> TOP_PRODUCTS;
            case VIP_CUSTOMERS -> VIP_CUSTOMERS;
            case UNSOLD_PRODUCTS -> UNSOLD_PRODUCTS;
        };
    }

    /**
     * Nội dung hint (không có dấu comment) cho báo cáo và JoinHint. LEADING cố định thứ tự join để
     * USE_NL/USE_HASH/USE_MERGE áp lên từng bảng bị join vào; thiếu LEADING thì CBO có thể đảo thứ tự
     * và bỏ qua hint. Báo cáo không có join hoặc AUTO: chuỗi rỗng.
     */
    static String hintBody(Report report, JoinHint hint) {
        if (!report.joinHintApplies() || hint == JoinHint.AUTO) {
            return "";
        }
        String method = switch (hint) {
            case NL -> "USE_NL";
            case HASH -> "USE_HASH";
            case MERGE -> "USE_MERGE";
            case AUTO -> throw new IllegalStateException();
        };
        return switch (report) {
            case REVENUE_BY_CATEGORY, TOP_PRODUCTS -> "LEADING(o oi p c) " + method + "(oi p c)";
            case VIP_CUSTOMERS -> "LEADING(o u) " + method + "(u)";
            default -> "";
        };
    }

    /** Ghép hint (có thể thêm GATHER_PLAN_STATISTICS khi lấy plan) vào template. */
    static String render(Report report, JoinHint hint, boolean gatherPlanStatistics) {
        String body = hintBody(report, hint);
        if (gatherPlanStatistics) {
            body = body.isEmpty() ? "GATHER_PLAN_STATISTICS" : "GATHER_PLAN_STATISTICS " + body;
        }
        String comment = body.isEmpty() ? "" : "/*+ " + body + " */ ";
        return template(report).replace(HINT, comment);
    }
}

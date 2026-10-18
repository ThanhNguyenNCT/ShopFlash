package com.shopflash.modules.query.model;

import java.util.Arrays;

/** Các báo cáo của module, slug là {report} trong URL. */
public enum Report {
    REVENUE_BY_MONTH("revenue-by-month", false, false),
    REVENUE_BY_CATEGORY("revenue-by-category", true, false),
    TOP_PRODUCTS("top-products", true, false),
    VIP_CUSTOMERS("vip-customers", true, true),
    UNSOLD_PRODUCTS("unsold-products", false, true);

    private final String slug;
    private final boolean joinHintApplies;
    private final boolean paged;

    Report(String slug, boolean joinHintApplies, boolean paged) {
        this.slug = slug;
        this.joinHintApplies = joinHintApplies;
        this.paged = paged;
    }

    public String slug() { return slug; }

    public boolean joinHintApplies() { return joinHintApplies; }

    public boolean paged() { return paged; }

    /** null nếu slug không tồn tại; controller đổi thành VALIDATION_ERROR. */
    public static Report fromSlug(String slug) {
        return Arrays.stream(values()).filter(r -> r.slug.equals(slug)).findFirst().orElse(null);
    }
}

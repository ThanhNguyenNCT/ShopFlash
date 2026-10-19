package com.shopflash.modules.storage.service;

import com.shopflash.common.web.ApiException;
import java.text.Normalizer;

/** Tạo slug ổn định khi admin không nhập slug; uniqueness vẫn được Oracle kiểm soát. */
final class Slugifier {

    private Slugifier() {
    }

    static String value(String proposed, String fallback) {
        String raw = proposed == null || proposed.isBlank() ? fallback : proposed;
        String slug = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'd')
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) throw ApiException.badRequest("slug", "Slug không hợp lệ");
        return slug;
    }
}

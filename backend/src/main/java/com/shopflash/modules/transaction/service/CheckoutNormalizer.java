package com.shopflash.modules.transaction.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.transaction.dto.CheckoutRequest;

final class CheckoutNormalizer {
    private static final Pattern UUID_KEY = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999999.99");
    private static final Pattern RECIPIENT_PHONE = Pattern.compile("(?:0|\\+84)[35789][0-9]{8}");

    private CheckoutNormalizer() {}

    static CheckoutCommand normalize(long userId, String rawKey, CheckoutRequest request) {
        if (rawKey == null || !UUID_KEY.matcher(rawKey).matches()) {
            throw ApiException.badRequest("Idempotency-Key", "Khóa yêu cầu phải là UUID chuẩn");
        }
        if (request == null) throw ApiException.badRequest("body", "Thiếu nội dung đặt hàng");
        if (request.cartVersion() < 0) throw ApiException.badRequest("cartVersion", "Phiên bản giỏ không hợp lệ");
        String name = text(request.recipientName(), "recipientName", 150);
        String phone = text(request.recipientPhone(), "recipientPhone", 30);
        if (!RECIPIENT_PHONE.matcher(phone).matches()) {
            throw ApiException.badRequest("recipientPhone",
                    "Nhập số di động Việt Nam hợp lệ, ví dụ 0912345678 hoặc +84912345678.");
        }
        String address = text(request.shippingAddress(), "shippingAddress", 500);
        if (request.expectedLines() == null || request.expectedLines().isEmpty()) {
            throw ApiException.badRequest("expectedLines", "Danh sách sản phẩm không được rỗng");
        }
        Set<Long> ids = new HashSet<>();
        List<CheckoutCommand.Line> lines = request.expectedLines().stream().map(line -> {
            if (line == null || line.productId() <= 0 || !ids.add(line.productId())) {
                throw ApiException.badRequest("expectedLines", "ID sản phẩm thiếu hoặc bị trùng");
            }
            if (line.quantity() < 1 || line.quantity() > 999) {
                throw ApiException.badRequest("expectedLines.quantity", "Số lượng phải từ 1 đến 999");
            }
            return new CheckoutCommand.Line(line.productId(), line.quantity(), money(line.unitPrice(), "unitPrice"));
        }).sorted(Comparator.comparingLong(CheckoutCommand.Line::productId)).toList();
        StringBuilder canonical = new StringBuilder("shopflash-checkout-v1\n");
        field(canonical, Long.toString(userId));
        field(canonical, Long.toString(request.cartVersion()));
        field(canonical, name);
        field(canonical, phone);
        field(canonical, address);
        field(canonical, Integer.toString(lines.size()));
        for (CheckoutCommand.Line line : lines) {
            field(canonical, Long.toString(line.productId()));
            field(canonical, Integer.toString(line.quantity()));
            field(canonical, line.unitPrice().toPlainString());
        }
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM thiếu SHA-256", e);
        }
        return new CheckoutCommand(userId, rawKey.toLowerCase(Locale.ROOT), hash,
                request.cartVersion(), name, phone, address, lines);
    }

    static BigDecimal money(BigDecimal input, String field) {
        if (input == null) throw ApiException.badRequest(field, "Thiếu số tiền");
        try {
            BigDecimal value = input.setScale(2, java.math.RoundingMode.UNNECESSARY);
            if (value.signum() < 0 || value.compareTo(MAX_MONEY) > 0) throw new ArithmeticException();
            return value;
        } catch (ArithmeticException e) {
            throw ApiException.badRequest(field, "Số tiền vượt giới hạn hoặc có quá hai chữ số thập phân");
        }
    }

    private static String text(String raw, String field, int maxLength) {
        if (raw == null) throw ApiException.badRequest(field, "Thông tin nhận hàng không được trống");
        String value = Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        // Oracle VARCHAR2 trong V1 dùng giới hạn byte; kiểm tra trước khi câu INSERT chạy.
        if (value.isEmpty() || value.getBytes(StandardCharsets.UTF_8).length > maxLength) {
            throw ApiException.badRequest(field, "Thông tin nhận hàng không hợp lệ");
        }
        return value;
    }

    private static void field(StringBuilder output, String value) {
        output.append(value.length()).append(':').append(value).append('\n');
    }
}

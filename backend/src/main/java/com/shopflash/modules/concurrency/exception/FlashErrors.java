package com.shopflash.modules.concurrency.exception;

import org.springframework.http.HttpStatus;

import com.shopflash.common.web.ApiException;

/** Lỗi nghiệp vụ của module concurrency, theo bảng "Lỗi" trong AGENTS.md của module. */
public final class FlashErrors {

    private FlashErrors() {
    }

    /** Đổi mã trả về của Lua script (reserve, claim) thành lỗi API. */
    public static ApiException fromScript(String code) {
        return switch (code) {
            case "SALE_NOT_FOUND" -> ApiException.notFound("FLASH_SALE_NOT_FOUND",
                    "Không tìm thấy chương trình flash sale đang mở");
            case "SALE_NOT_STARTED" -> ApiException.conflict("SALE_NOT_STARTED", "Chương trình chưa bắt đầu");
            case "SALE_ENDED" -> ApiException.conflict("SALE_ENDED", "Chương trình đã kết thúc");
            case "ITEM_UNAVAILABLE" -> ApiException.conflict("ITEM_UNAVAILABLE",
                    "Sản phẩm này không còn bán trong chương trình");
            case "SOLD_OUT" -> ApiException.conflict("SOLD_OUT", "Đã hết suất");
            case "USER_LIMIT_REACHED" -> ApiException.conflict("USER_LIMIT_REACHED",
                    "Bạn đã mua đủ số lượng cho phép của sản phẩm này");
            case "INVALID_QUANTITY" -> ApiException.badRequest("quantity", "Số lượng phải lớn hơn 0");
            case "NOT_FOUND" -> reservationNotFound();
            case "EXPIRED" -> ApiException.conflict("RESERVATION_EXPIRED", "Suất giữ đã hết hạn, vui lòng giữ suất lại");
            case "ALREADY_CLAIMED" -> ApiException.conflict("RESERVATION_ALREADY_USED", "Suất này đang được đặt đơn");
            default -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                    "Lỗi hệ thống, vui lòng thử lại", null, new IllegalStateException("Mã Lua không rõ: " + code));
        };
    }

    public static ApiException reservationNotFound() {
        return ApiException.notFound("RESERVATION_NOT_FOUND", "Không tìm thấy suất đã giữ");
    }

    public static ApiException saleNotFound() {
        return ApiException.notFound("FLASH_SALE_NOT_FOUND", "Không tìm thấy chương trình flash sale");
    }

    public static ApiException redisUnavailable(Throwable cause) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REDIS_UNAVAILABLE",
                "Không kết nối được Redis, vui lòng thử lại sau", null, cause);
    }
}

package com.shopflash.modules.transaction.service;

import java.sql.SQLException;
import java.util.Locale;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.transaction.dto.CheckoutRequest;
import com.shopflash.modules.transaction.dto.PlacedOrderResponse;
import com.shopflash.modules.transaction.repository.CheckoutRepository;
import com.shopflash.modules.transaction.repository.CheckoutRepository.StoredOrder;

@Service
public class CheckoutService {
    private final CheckoutRepository checkout;
    private final CheckoutWriter writer;

    public CheckoutService(CheckoutRepository checkout, CheckoutWriter writer) {
        this.checkout = checkout;
        this.writer = writer;
    }

    public PlacedOrderResponse place(long userId, String key, CheckoutRequest request) {
        CheckoutCommand command = CheckoutNormalizer.normalize(userId, key, request);
        StoredOrder existing = checkout.storedByKey(command.key());
        if (existing != null) return reuseOrConflict(command, existing);
        try {
            return writer.place(command);
        } catch (DataIntegrityViolationException e) {
            if (!oracleCode(e, 1)) throw e;
            // Transaction của writer đã rollback; truy vấn sau đây bắt đầu ở connection khác.
            StoredOrder winner = checkout.storedByKey(command.key());
            if (winner != null) return reuseOrConflict(command, winner);
            String detail = String.valueOf(e.getMostSpecificCause().getMessage()).toUpperCase(Locale.ROOT);
            if (detail.contains("UK_ORDERS_CODE")) {
                throw ApiException.conflict("ORDER_CODE_CONFLICT", "Mã đơn bị trùng; hãy gửi lại cùng khóa và nội dung");
            }
            if (detail.contains("UK_ORDERS_IDEMPOTENCY")) {
                throw ApiException.conflict("RETRY_SAME_REQUEST", "Yêu cầu đang hoàn tất; hãy gửi lại cùng khóa và nội dung");
            }
            throw e;
        } catch (DataAccessException e) {
            if (oracleCode(e, 60)) {
                throw ApiException.conflict("DEADLOCK", "Giao dịch bị hoàn tác do tranh chấp; hãy gửi lại cùng khóa và nội dung");
            }
            if (oracleCode(e, 30006) || oracleCode(e, 54)) {
                throw ApiException.conflict("LOCK_TIMEOUT", "Hết thời gian chờ khóa; hãy gửi lại cùng khóa và nội dung");
            }
            throw e;
        }
    }

    static PlacedOrderResponse reuseOrConflict(CheckoutCommand command, StoredOrder order) {
        if (order.userId() == command.userId() && command.hash().equals(order.hash())) return order.response();
        throw ApiException.conflict("IDEMPOTENCY_CONFLICT", "Khóa yêu cầu đã được sử dụng cho nội dung khác");
    }

    private static boolean oracleCode(Throwable error, int code) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == code) return true;
        }
        return false;
    }
}

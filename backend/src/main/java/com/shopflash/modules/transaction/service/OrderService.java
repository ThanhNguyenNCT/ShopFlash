package com.shopflash.modules.transaction.service;

import java.util.Set;
import java.sql.SQLException;

import org.springframework.dao.DataAccessException;

import org.springframework.stereotype.Service;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.transaction.dto.OrderDetailResponse;
import com.shopflash.modules.transaction.dto.OrderListResponse;
import com.shopflash.modules.transaction.repository.OrderRepository;

@Service
public class OrderService {
    private static final Set<String> STATUSES = Set.of("PENDING", "CONFIRMED", "COMPLETED", "CANCELLED");

    private final OrderRepository orders;
    private final OrderCancellationWriter cancellation;

    public OrderService(OrderRepository orders, OrderCancellationWriter cancellation) {
        this.orders = orders;
        this.cancellation = cancellation;
    }

    public OrderListResponse list(long userId, int page, int size, String status) {
        if (page < 1) throw ApiException.badRequest("page", "Trang phải bắt đầu từ 1");
        if (size < 1 || size > 100) throw ApiException.badRequest("size", "Kích thước trang phải từ 1 đến 100");
        if (status != null && !STATUSES.contains(status)) {
            throw ApiException.badRequest("status", "Trạng thái đơn không hợp lệ");
        }
        return orders.list(userId, page, size, status);
    }

    public OrderDetailResponse detail(long userId, long orderId) {
        if (orderId < 1) throw ApiException.badRequest("orderId", "Mã đơn phải lớn hơn 0");
        OrderDetailResponse order = orders.detail(userId, orderId);
        if (order == null) throw ApiException.notFound("ORDER_NOT_FOUND", "Không tìm thấy đơn hàng");
        return order;
    }

    public OrderDetailResponse cancel(long userId, long orderId) {
        if (orderId < 1) throw ApiException.badRequest("orderId", "Mã đơn phải lớn hơn 0");
        try {
            return cancellation.cancel(userId, orderId);
        } catch (DataAccessException e) {
            if (oracleCode(e, 60)) {
                throw ApiException.conflict("DEADLOCK", "Giao dịch bị hoàn tác do tranh chấp; hãy tải lại đơn trước khi thử lại");
            }
            if (oracleCode(e, 30006) || oracleCode(e, 54)) {
                throw ApiException.conflict("LOCK_TIMEOUT", "Hết thời gian chờ khóa; hãy tải lại đơn trước khi thử lại");
            }
            throw e;
        }
    }

    private static boolean oracleCode(Throwable error, int code) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == code) return true;
        }
        return false;
    }
}

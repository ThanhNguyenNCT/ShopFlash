package com.shopflash.modules.transaction.service;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.transaction.dto.OrderDetailResponse;
import com.shopflash.modules.transaction.repository.OrderRepository;
import com.shopflash.modules.transaction.repository.OrderRepository.LockedOrder;
import com.shopflash.modules.transaction.repository.OrderRepository.RestoreLine;

@Service
public class OrderCancellationWriter {
    private final OrderRepository orders;
    private final ObjectProvider<OrderCancellationProbe> probes;

    public OrderCancellationWriter(OrderRepository orders, ObjectProvider<OrderCancellationProbe> probes) {
        this.orders = orders;
        this.probes = probes;
    }

    @Transactional
    public OrderDetailResponse cancel(long userId, long orderId) {
        probes.ifAvailable(OrderCancellationProbe::beforeOrderLock);
        LockedOrder order = orders.lock(userId, orderId);
        if (order == null) throw ApiException.notFound("ORDER_NOT_FOUND", "Không tìm thấy đơn hàng");
        probes.ifAvailable(OrderCancellationProbe::afterOrderLock);
        if (!"NORMAL".equals(order.orderType()) || !"PENDING".equals(order.status())) {
            throw ApiException.conflict("ORDER_NOT_CANCELLABLE", "Đơn hàng không còn đủ điều kiện hủy");
        }

        List<RestoreLine> lines = orders.restoreLines(orderId);
        // Khóa hết Inventory theo ID tăng dần trước khi ghi dòng đầu tiên.
        for (RestoreLine line : lines) {
            Long onHand = orders.lockInventory(line.productId());
            if (onHand == null || line.quantity() > 9999999999L - onHand) {
                throw ApiException.conflict("INVENTORY_CONFLICT", "Không thể hoàn tồn kho cho đơn hàng");
            }
        }
        for (RestoreLine line : lines) {
            if (orders.restoreInventory(line.productId(), line.quantity()) != 1) {
                throw ApiException.conflict("INVENTORY_CONFLICT", "Không thể hoàn tồn kho cho đơn hàng");
            }
            probes.ifAvailable(probe -> probe.afterInventoryRestore(line.productId()));
        }
        if (orders.markCancelled(userId, orderId) != 1) {
            throw ApiException.conflict("ORDER_NOT_CANCELLABLE", "Đơn hàng không còn đủ điều kiện hủy");
        }
        return orders.detail(userId, orderId);
    }
}

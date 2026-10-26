package com.shopflash.modules.transaction.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.transaction.dto.PlacedOrderResponse;
import com.shopflash.modules.transaction.repository.CartRepository;
import com.shopflash.modules.transaction.repository.CartRepository.CartState;
import com.shopflash.modules.transaction.repository.CheckoutRepository;
import com.shopflash.modules.transaction.repository.CheckoutRepository.CartLine;
import com.shopflash.modules.transaction.repository.CheckoutRepository.InventoryState;
import com.shopflash.modules.transaction.repository.CheckoutRepository.ProductState;
import com.shopflash.modules.transaction.repository.CheckoutRepository.StoredOrder;

@Service
public class CheckoutWriter {
    private final CartRepository carts;
    private final CheckoutRepository checkout;
    private final OrderCodeGenerator codes;
    private final ObjectProvider<CheckoutProbe> probes;

    public CheckoutWriter(CartRepository carts, CheckoutRepository checkout,
                          OrderCodeGenerator codes, ObjectProvider<CheckoutProbe> probes) {
        this.carts = carts;
        this.checkout = checkout;
        this.codes = codes;
        this.probes = probes;
    }

    /** Toàn bộ ghi Oracle nằm trong một transaction; caller xử lý lỗi unique sau khi proxy đã rollback. */
    @Transactional
    public PlacedOrderResponse place(CheckoutCommand command) {
        probe(CheckoutProbe::beforeCartLock);
        CartState cart = carts.lock(command.userId());
        StoredOrder duplicate = checkout.storedByKey(command.key());
        if (duplicate != null) return CheckoutService.reuseOrConflict(command, duplicate);
        if (cart == null) throw ApiException.conflict("CART_EMPTY", "Giỏ hàng đang trống");
        probe(CheckoutProbe::afterCartLock);
        if (cart.version() != command.cartVersion()) {
            throw ApiException.conflict("CART_CHANGED", "Giỏ đã thay đổi; hãy xem lại trước khi đặt hàng");
        }
        List<CartLine> cartLines = checkout.cartLines(cart.cartId());
        if (cartLines.isEmpty()) throw ApiException.conflict("CART_EMPTY", "Giỏ hàng đang trống");
        if (cartLines.size() != command.expectedLines().size()) throw cartChanged();
        for (int i = 0; i < cartLines.size(); i++) {
            CartLine actual = cartLines.get(i);
            CheckoutCommand.Line expected = command.expectedLines().get(i);
            if (actual.productId() != expected.productId() || actual.quantity() != expected.quantity()) {
                throw cartChanged();
            }
        }

        // Khóa toàn bộ PRODUCTS theo ID trước khi lấy bất kỳ khóa Inventory nào.
        List<ProductState> products = new ArrayList<>();
        for (CheckoutCommand.Line line : command.expectedLines()) {
            ProductState product = checkout.lockProduct(line.productId());
            if (product == null || !"ACTIVE".equals(product.status())) {
                throw ApiException.conflict("PRODUCT_UNAVAILABLE", "Sản phẩm đã ngừng bán hoặc không còn tồn tại");
            }
            products.add(product);
        }
        probe(CheckoutProbe::afterProductLock);
        BigDecimal total = new BigDecimal("0.00");
        for (int i = 0; i < products.size(); i++) {
            ProductState product = products.get(i);
            CheckoutCommand.Line line = command.expectedLines().get(i);
            if (product.price().compareTo(line.unitPrice()) != 0) {
                throw ApiException.conflict("PRICE_CHANGED", "Giá đã đổi; hãy xem lại đơn hàng");
            }
            total = CheckoutNormalizer.money(total.add(
                    CheckoutNormalizer.money(product.price().multiply(BigDecimal.valueOf(line.quantity())),
                            "lineTotal")), "totalAmount");
        }
        // Inventory cũng được khóa theo ID tăng dần rồi mới bắt đầu ghi.
        for (CheckoutCommand.Line line : command.expectedLines()) {
            InventoryState inventory = checkout.lockInventory(line.productId());
            if (inventory == null || inventory.onHand() - inventory.reservedFlash() < line.quantity()) {
                throw ApiException.conflict("OUT_OF_STOCK", "Không đủ tồn kho có thể bán");
            }
        }

        probe(CheckoutProbe::beforeOrderInsert);
        checkout.insertOrder(command, codes.next(), total);
        StoredOrder created = checkout.storedByKey(command.key());
        if (created == null) throw new IllegalStateException("Không tìm thấy đơn vừa tạo trong giao dịch");
        probe(CheckoutProbe::afterOrderWrite);
        for (int i = 0; i < products.size(); i++) {
            checkout.insertOrderItem(created.orderId(), products.get(i), command.expectedLines().get(i).quantity());
        }
        probe(CheckoutProbe::afterOrderItemsWrite);
        for (CheckoutCommand.Line line : command.expectedLines()) {
            if (checkout.decreaseInventory(line.productId(), line.quantity()) != 1) {
                throw ApiException.conflict("OUT_OF_STOCK", "Tồn kho vừa thay đổi; hãy xem lại đơn hàng");
            }
        }
        probe(CheckoutProbe::afterInventoryWrite);
        probe(CheckoutProbe::beforeCartClear);
        for (CheckoutCommand.Line line : command.expectedLines()) {
            if (carts.deleteItem(cart.cartId(), line.productId()) != 1) throw cartChanged();
        }
        carts.incrementVersion(cart.cartId());
        return created.response();
    }

    private void probe(java.util.function.Consumer<CheckoutProbe> action) {
        probes.ifAvailable(action);
    }

    private static ApiException cartChanged() {
        return ApiException.conflict("CART_CHANGED", "Giỏ đã thay đổi; hãy xem lại trước khi đặt hàng");
    }
}

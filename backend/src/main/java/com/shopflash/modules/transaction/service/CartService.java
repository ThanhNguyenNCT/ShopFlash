package com.shopflash.modules.transaction.service;

import java.sql.SQLException;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.transaction.dto.CartResponse;
import com.shopflash.modules.transaction.repository.CartRepository;
import com.shopflash.modules.transaction.repository.CartRepository.CartState;

@Service
public class CartService {
    private static final int MAX_QUANTITY = 999;
    private final CartRepository carts;

    public CartService(CartRepository carts) {
        this.carts = carts;
    }

    @Transactional
    public CartResponse add(long userId, long expectedVersion, long productId, int quantity) {
        validate(userId, expectedVersion, productId, quantity);
        return translateLockError(() -> {
            CartState cart = carts.lock(userId);
            checkVersion(cart, expectedVersion);
            if (cart == null) {
                try {
                    carts.create(userId);
                } catch (DataIntegrityViolationException e) {
                    if (!hasOracleCode(e, 1)) throw e;
                    // Ràng buộc uk_carts_user phân định hai request cùng tạo giỏ. Transaction này rollback.
                    throw ApiException.conflict("CART_CHANGED", "Giỏ đã được tạo ở yêu cầu khác; hãy tải lại giỏ");
                }
                cart = carts.lock(userId);
            }
            requireActiveProduct(productId);
            Integer current = carts.quantity(cart.cartId(), productId);
            long next = (long) (current == null ? 0 : current) + quantity;
            if (next > MAX_QUANTITY) throw ApiException.badRequest("quantity", "Mỗi mặt hàng có tối đa 999 sản phẩm");
            if (current == null) carts.insertItem(cart.cartId(), productId, (int) next);
            else carts.setQuantity(cart.cartId(), productId, (int) next);
            carts.incrementVersion(cart.cartId());
            return carts.read(userId);
        });
    }

    @Transactional
    public CartResponse setQuantity(long userId, long expectedVersion, long productId, int quantity) {
        validate(userId, expectedVersion, productId, quantity);
        return translateLockError(() -> {
            CartState cart = carts.lock(userId);
            checkVersion(cart, expectedVersion);
            if (cart == null || carts.quantity(cart.cartId(), productId) == null) throw missingItem();
            carts.setQuantity(cart.cartId(), productId, quantity);
            carts.incrementVersion(cart.cartId());
            return carts.read(userId);
        });
    }

    @Transactional
    public CartResponse remove(long userId, long expectedVersion, long productId) {
        validateIds(userId, expectedVersion, productId);
        return translateLockError(() -> {
            CartState cart = carts.lock(userId);
            checkVersion(cart, expectedVersion);
            if (cart == null || carts.deleteItem(cart.cartId(), productId) == 0) throw missingItem();
            carts.incrementVersion(cart.cartId());
            return carts.read(userId);
        });
    }

    @Transactional
    public CartResponse clear(long userId, long expectedVersion) {
        if (userId <= 0) throw ApiException.badRequest("userId", "Danh tính khách hàng không hợp lệ");
        if (expectedVersion < 0) throw ApiException.badRequest("X-Cart-Version", "Phiên bản giỏ không hợp lệ");
        return translateLockError(() -> {
            CartState cart = carts.lock(userId);
            checkVersion(cart, expectedVersion);
            if (cart != null && carts.deleteAll(cart.cartId()) > 0) carts.incrementVersion(cart.cartId());
            return carts.read(userId);
        });
    }

    private void requireActiveProduct(long productId) {
        String status = carts.productStatus(productId);
        if (status == null) throw ApiException.notFound("PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm");
        if (!"ACTIVE".equals(status)) throw ApiException.conflict("PRODUCT_UNAVAILABLE", "Sản phẩm đã ngừng bán");
    }

    private static void checkVersion(CartState cart, long expected) {
        long actual = cart == null ? 0 : cart.version();
        if (actual != expected) throw ApiException.conflict("CART_CHANGED", "Giỏ đã thay đổi; hãy tải lại giỏ");
    }

    private static ApiException missingItem() {
        return ApiException.notFound("CART_ITEM_NOT_FOUND", "Sản phẩm không có trong giỏ");
    }

    private static void validate(long userId, long expectedVersion, long productId, int quantity) {
        validateIds(userId, expectedVersion, productId);
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw ApiException.badRequest("quantity", "Số lượng phải từ 1 đến 999");
        }
    }

    private static void validateIds(long userId, long expectedVersion, long productId) {
        if (userId <= 0) throw ApiException.badRequest("userId", "Danh tính khách hàng không hợp lệ");
        if (expectedVersion < 0) throw ApiException.badRequest("X-Cart-Version", "Phiên bản giỏ không hợp lệ");
        if (productId <= 0) throw ApiException.badRequest("productId", "Mã sản phẩm phải dương");
    }

    private static CartResponse translateLockError(Supplier<CartResponse> action) {
        try {
            return action.get();
        } catch (DataAccessException e) {
            if (hasOracleCode(e, 30006) || hasOracleCode(e, 54)) {
                throw ApiException.conflict("LOCK_TIMEOUT", "Giỏ đang được cập nhật; hãy tải lại giỏ trước khi thử lại");
            }
            if (hasOracleCode(e, 60)) {
                throw ApiException.conflict("DEADLOCK", "Giao dịch bị xung đột; hãy tải lại giỏ trước khi thử lại");
            }
            throw e;
        }
    }

    private static boolean hasOracleCode(Throwable error, int code) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == code) return true;
        }
        return false;
    }
}

package com.shopflash.modules.transaction.cart;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shopflash.common.web.ApiException;
import com.shopflash.common.web.GlobalExceptionHandler;
import com.shopflash.modules.transaction.controller.CartController;
import com.shopflash.modules.transaction.dto.CartResponse;
import com.shopflash.modules.transaction.repository.CartRepository;
import com.shopflash.modules.transaction.security.TransactionCustomer;
import com.shopflash.modules.transaction.security.TransactionIdentity;
import com.shopflash.modules.transaction.service.CartService;

class CartCoreTest {
    @Test
    void readsOnlyCartOfBackendResolvedCustomer() throws Exception {
        TransactionIdentity identity = mock(TransactionIdentity.class);
        CartRepository carts = mock(CartRepository.class);
        when(identity.requireCustomer(any(HttpServletRequest.class)))
                .thenReturn(new TransactionCustomer(42, "customer@example.test", "CUSTOMER"));
        when(carts.read(42)).thenReturn(new CartResponse(3, List.of(
                new CartResponse.Item(11, "Sản phẩm", "SKU-11", 2,
                        new BigDecimal("12.50"), new BigDecimal("25.00"))), new BigDecimal("25.00")));

        mvc(identity, carts).perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.items[0].productId").value(11))
                .andExpect(jsonPath("$.items[0].lineTotal").value(25.00))
                .andExpect(jsonPath("$.totalAmount").value(25.00));
        verify(carts).read(42);
    }

    @Test
    void refusesUnauthenticatedReadBeforeQueryingOracle() throws Exception {
        TransactionIdentity identity = mock(TransactionIdentity.class);
        CartRepository carts = mock(CartRepository.class);
        when(identity.requireCustomer(any(HttpServletRequest.class)))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Cần token khách hàng hợp lệ"));

        mvc(identity, carts).perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
        verifyNoInteractions(carts);
    }

    private static MockMvc mvc(TransactionIdentity identity, CartRepository carts) {
        return MockMvcBuilders.standaloneSetup(new CartController(identity, carts, mock(CartService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}

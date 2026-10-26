package com.shopflash.modules.transaction.cart;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.shopflash.common.web.GlobalExceptionHandler;
import com.shopflash.common.security.AuthPrincipal;
import com.shopflash.modules.transaction.controller.CartController;
import com.shopflash.modules.transaction.dto.CartResponse;
import com.shopflash.modules.transaction.repository.CartRepository;
import com.shopflash.modules.transaction.security.TransactionIdentity;
import com.shopflash.modules.transaction.security.JwtTransactionIdentity;
import com.shopflash.modules.transaction.service.CartService;

class CartCoreTest {
    @Test
    void readsOnlyCartOfBackendResolvedCustomer() throws Exception {
        TransactionIdentity identity = new JwtTransactionIdentity();
        CartRepository carts = mock(CartRepository.class);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthPrincipal(42, "customer@example.test", "Customer", "CUSTOMER"), null, List.of()));
        when(carts.read(42)).thenReturn(new CartResponse(3, List.of(
                new CartResponse.Item(11, "Sản phẩm", "SKU-11", 2,
                        new BigDecimal("12.50"), new BigDecimal("25.00"))), new BigDecimal("25.00")));

        try {
            mvc(identity, carts).perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.items[0].productId").value(11))
                .andExpect(jsonPath("$.items[0].lineTotal").value(25.00))
                .andExpect(jsonPath("$.totalAmount").value(25.00));
            verify(carts).read(42);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void refusesUnauthenticatedReadBeforeQueryingOracle() throws Exception {
        TransactionIdentity identity = new JwtTransactionIdentity();
        CartRepository carts = mock(CartRepository.class);

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

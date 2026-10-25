package com.shopflash.modules.transaction.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopflash.common.web.ApiException;
import com.shopflash.common.web.GlobalExceptionHandler;
import com.shopflash.modules.transaction.controller.LocalIdentityController;

class LocalTransactionIdentityCoreTest {
    private static final String TOKEN = "a".repeat(64);
    private static final String EMAIL = "customer@shopflash.local";

    @TempDir Path temp;

    @Test
    void validTokenResolvesCustomerWithoutTrustingClientUserId() throws IOException {
        TransactionCustomerRepository customers = mock(TransactionCustomerRepository.class);
        TransactionCustomer customer = new TransactionCustomer(2, EMAIL, "CUSTOMER");
        when(customers.findActiveCustomer(EMAIL)).thenReturn(customer);
        LocalTransactionIdentity identity = new LocalTransactionIdentity("127.0.0.1", tokenFile(), customers);
        MockHttpServletRequest request = request(TOKEN);

        assertThat(identity.requireCustomer(request)).isEqualTo(customer);
        assertThat(new LocalIdentityController(identity).currentCustomer(request)).isEqualTo(customer);

        request.addHeader("X-User-Id", "999");
        assertCode(identity, request, "VALIDATION_ERROR");
        MockHttpServletRequest withQuery = request(TOKEN);
        withQuery.addParameter("userId", "999");
        assertCode(identity, withQuery, "VALIDATION_ERROR");
    }

    @Test
    void rejectsMissingWrongTokenAndInactiveOrNonCustomerAccount() throws IOException {
        TransactionCustomerRepository customers = mock(TransactionCustomerRepository.class);
        LocalTransactionIdentity identity = new LocalTransactionIdentity("127.0.0.1", tokenFile(), customers);

        assertCode(identity, new MockHttpServletRequest(), "AUTH_REQUIRED");
        assertCode(identity, request("b".repeat(64)), "AUTH_REQUIRED");
        assertCode(identity, request(TOKEN), "CUSTOMER_REQUIRED");
    }

    @Test
    void profileDoesNotExposeLocalEndpointByDefaultAndRejectsUnsafeStartup() throws IOException {
        try (AnnotationConfigApplicationContext context = context("default", "127.0.0.1", tokenFile())) {
            assertThat(context.getBeansOfType(LocalIdentityController.class)).isEmpty();
            assertThat(context.getBean(TransactionIdentity.class)).isInstanceOf(UnavailableTransactionIdentity.class);
        }
        assertThatThrownBy(() -> context("tx-local", "0.0.0.0", tokenFile()))
                .hasRootCauseMessage("tx-local chỉ được bật khi server.address=127.0.0.1");
        assertThatThrownBy(() -> context("tx-local", "127.0.0.1", ""))
                .hasRootCauseMessage("Thiếu TX_LOCAL_TOKEN_FILE cho profile tx-local");
    }

    @Test
    void rejectsInvalidTokenFile() throws IOException {
        TransactionCustomerRepository customers = mock(TransactionCustomerRepository.class);
        Path file = temp.resolve("bad.csv");
        Files.writeString(file, "token,email\nnot-a-token," + EMAIL + "\n");
        assertThatThrownBy(() -> new LocalTransactionIdentity("127.0.0.1", file.toString(), customers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Dòng token local không hợp lệ");
    }

    @Test
    void localIdentityEndpointUsesCommonErrorEnvelope() throws Exception {
        TransactionCustomerRepository customers = mock(TransactionCustomerRepository.class);
        when(customers.findActiveCustomer(EMAIL)).thenReturn(new TransactionCustomer(2, EMAIL, "CUSTOMER"));
        LocalTransactionIdentity identity = new LocalTransactionIdentity("127.0.0.1", tokenFile(), customers);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new LocalIdentityController(identity))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/transaction/local-identity").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.email").value(EMAIL));
        mvc.perform(get("/api/transaction/local-identity"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
        mvc.perform(get("/api/transaction/local-identity")
                        .header("Authorization", "Bearer " + TOKEN).header("X-User-Id", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    private AnnotationConfigApplicationContext context(String profile, String address, String file) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
                "server.address", address, "TX_LOCAL_TOKEN_FILE", file)));
        context.registerBean(TransactionCustomerRepository.class, () -> mock(TransactionCustomerRepository.class));
        context.register(LocalTransactionIdentity.class, UnavailableTransactionIdentity.class, LocalIdentityController.class);
        try {
            context.refresh();
            return context;
        } catch (RuntimeException e) {
            context.close();
            throw e;
        }
    }

    private String tokenFile() throws IOException {
        Path file = temp.resolve("tokens.csv");
        Files.writeString(file, "token,email\n" + TOKEN + "," + EMAIL + "\n");
        return file.toString();
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static void assertCode(TransactionIdentity identity, MockHttpServletRequest request, String expected) {
        assertThatThrownBy(() -> identity.requireCustomer(request))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(expected));
    }
}

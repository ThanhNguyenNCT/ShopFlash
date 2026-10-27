package com.shopflash.modules.transaction.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.transaction.dto.CheckoutRequest;
import com.shopflash.modules.transaction.repository.CheckoutRepository;
import com.shopflash.modules.transaction.service.CheckoutService;
import com.shopflash.modules.transaction.service.CheckoutWriter;

class CheckoutCoreTest {
    @Test
    void rejectsNonCanonicalMoneyDuplicateLinesAndRecipientOverOracleByteLimit() {
        CheckoutService checkout = new CheckoutService(mock(CheckoutRepository.class), mock(CheckoutWriter.class));
        for (CheckoutRequest request : List.of(
                new CheckoutRequest(0, "Khách", "0900000000", "Địa chỉ",
                        List.of(new CheckoutRequest.ExpectedLine(1, 1, new BigDecimal("100.001")))),
                new CheckoutRequest(0, "Khách", "0900000000", "Địa chỉ", List.of(
                        new CheckoutRequest.ExpectedLine(1, 1, new BigDecimal("100.00")),
                        new CheckoutRequest.ExpectedLine(1, 1, new BigDecimal("100.00")))),
                new CheckoutRequest(0, "ứ".repeat(80), "0900000000", "Địa chỉ",
                        List.of(new CheckoutRequest.ExpectedLine(1, 1, new BigDecimal("100.00")))))) {
            assertThatThrownBy(() -> checkout.place(1, UUID.randomUUID().toString(), request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(error -> assertThat(((ApiException) error).status().value()).isEqualTo(400));
        }
    }

    @Test
    void mapsOracleDeadlockAndTimeoutToSafeRetryCodes() {
        for (int oracleCode : List.of(60, 30006)) {
            CheckoutRepository repository = mock(CheckoutRepository.class);
            CheckoutWriter writer = mock(CheckoutWriter.class);
            when(writer.place(any())).thenThrow(new CannotAcquireLockException("Oracle lock",
                    new SQLException("Oracle lock", "61000", oracleCode)));
            CheckoutService checkout = new CheckoutService(repository, writer);
            CheckoutRequest request = new CheckoutRequest(0, "Khách", "0900000000", "Địa chỉ",
                    List.of(new CheckoutRequest.ExpectedLine(1, 1, new BigDecimal("100.00"))));
            assertThatThrownBy(() -> checkout.place(1, UUID.randomUUID().toString(), request))
                    .isInstanceOf(ApiException.class)
                    .satisfies(error -> assertThat(((ApiException) error).code())
                            .isEqualTo(oracleCode == 60 ? "DEADLOCK" : "LOCK_TIMEOUT"));
        }
    }
}

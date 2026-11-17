package com.shopflash.modules.transaction.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.transaction.dto.CheckoutRequest;
import com.shopflash.modules.transaction.repository.CheckoutRepository;
import com.shopflash.modules.transaction.service.CheckoutService;
import com.shopflash.modules.transaction.service.CheckoutWriter;
import com.shopflash.modules.transaction.service.CheckoutCommand;

class CheckoutCoreTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abc", "123", "091234567", "09123456789", "0112345678",
            "0012345678", "+840912345678", "84912345678", "09123abc78", "091 234 5678",
            "０９１２３４５６７８", "0912345678\n123"})
    void rejectsInvalidPhoneBeforeAccessingDatabase(String phone) {
        CheckoutRepository repository = mock(CheckoutRepository.class);
        CheckoutWriter writer = mock(CheckoutWriter.class);
        CheckoutService checkout = new CheckoutService(repository, writer);
        CheckoutRequest request = new CheckoutRequest(0, "Khách", phone, "Địa chỉ",
                List.of(new CheckoutRequest.ExpectedLine(1, 1, new BigDecimal("100.00"))));

        assertThatThrownBy(() -> checkout.place(1, UUID.randomUUID().toString(), request))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException problem = (ApiException) error;
                    assertThat(problem.status().value()).isEqualTo(400);
                    assertThat(problem.code()).isEqualTo("VALIDATION_ERROR");
                    assertThat(problem.details()).containsEntry("field", "recipientPhone");
                });
        verifyNoInteractions(repository, writer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0312345678", "0512345678", "0712345678", "0812345678", "0912345678",
            "+84312345678", "+84512345678", "+84712345678", "+84812345678", "+84912345678",
            " 0912345678 "})
    void acceptsDomesticAndInternationalMobilePhoneFormats(String phone) {
        CheckoutRepository repository = mock(CheckoutRepository.class);
        CheckoutWriter writer = mock(CheckoutWriter.class);
        CheckoutService checkout = new CheckoutService(repository, writer);
        CheckoutRequest request = new CheckoutRequest(0, "Khách", phone, "Địa chỉ",
                List.of(new CheckoutRequest.ExpectedLine(1, 1, new BigDecimal("100.00"))));

        checkout.place(1, UUID.randomUUID().toString(), request);

        ArgumentCaptor<CheckoutCommand> command = ArgumentCaptor.forClass(CheckoutCommand.class);
        verify(writer).place(command.capture());
        assertThat(command.getValue().recipientPhone()).isEqualTo(phone.strip());
    }

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

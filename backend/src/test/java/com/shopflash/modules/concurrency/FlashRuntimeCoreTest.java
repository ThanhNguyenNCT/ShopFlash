package com.shopflash.modules.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.shopflash.common.web.ApiException;
import com.shopflash.modules.concurrency.exception.FlashErrors;
import com.shopflash.modules.concurrency.model.ReleaseRule;
import com.shopflash.modules.concurrency.repository.redis.LuaScripts;
import com.shopflash.modules.concurrency.repository.redis.LuaScripts.Script;

/** Không cần Oracle, Redis: ./mvnw test -Dtest='FlashRuntimeCoreTest' */
class FlashRuntimeCoreTest {

    @Test
    void everyLuaScriptLoadsWithItsOwnSha() {
        LuaScripts scripts = new LuaScripts(null);
        Set<String> shas = new HashSet<>();
        for (Script script : Script.values()) {
            String sha = scripts.sha(script);
            assertThat(sha).matches("[0-9a-f]{40}");
            shas.add(sha);
        }
        assertThat(shas).hasSize(Script.values().length);
    }

    @Test
    void scriptErrorCodesBecomeApiErrors() {
        assertError("SOLD_OUT", HttpStatus.CONFLICT, "SOLD_OUT");
        assertError("USER_LIMIT_REACHED", HttpStatus.CONFLICT, "USER_LIMIT_REACHED");
        assertError("SALE_NOT_STARTED", HttpStatus.CONFLICT, "SALE_NOT_STARTED");
        assertError("SALE_ENDED", HttpStatus.CONFLICT, "SALE_ENDED");
        assertError("ITEM_UNAVAILABLE", HttpStatus.CONFLICT, "ITEM_UNAVAILABLE");
        assertError("SALE_NOT_FOUND", HttpStatus.NOT_FOUND, "FLASH_SALE_NOT_FOUND");
        assertError("NOT_FOUND", HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND");
        assertError("EXPIRED", HttpStatus.CONFLICT, "RESERVATION_EXPIRED");
        assertError("ALREADY_CLAIMED", HttpStatus.CONFLICT, "RESERVATION_ALREADY_USED");
        assertError("INVALID_QUANTITY", HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        // Mã lạ không lộ ra client.
        assertError("SOMETHING_ELSE", HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    }

    @Test
    void releaseRulesMatchTheReservationLifecycle() {
        assertThat(ReleaseRule.expiredPending()).isEqualTo(new ReleaseRule("PENDING", 0, null));
        assertThat(ReleaseRule.staleClaim(30_000)).isEqualTo(new ReleaseRule("CLAIMED", 30_000, null));
        assertThat(ReleaseRule.rollback()).isEqualTo(new ReleaseRule("CLAIMED", -1, null));
        assertThat(ReleaseRule.cancelledBy(7)).isEqualTo(new ReleaseRule("PENDING", -1, 7L));
        assertThat(Arrays.asList(ReleaseRule.rollback().allowedStatuses().split(","))).containsExactly("CLAIMED");
    }

    private static void assertError(String scriptCode, HttpStatus status, String apiCode) {
        ApiException e = FlashErrors.fromScript(scriptCode);
        assertThat(e.status()).as(scriptCode).isEqualTo(status);
        assertThat(e.code()).as(scriptCode).isEqualTo(apiCode);
    }
}

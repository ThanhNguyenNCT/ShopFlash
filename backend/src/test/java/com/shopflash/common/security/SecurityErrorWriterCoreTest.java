package com.shopflash.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class SecurityErrorWriterCoreTest {
    private final SecurityErrorWriter writer = new SecurityErrorWriter(JsonMapper.builder().build());

    @Test
    void writesSharedJsonShapeForUnauthorizedAndForbidden() throws Exception {
        MockHttpServletResponse unauthorized = new MockHttpServletResponse();
        writer.unauthorized(unauthorized);
        assertThat(unauthorized.getStatus()).isEqualTo(401);
        assertThat(unauthorized.getContentType()).startsWith("application/json");
        assertThat(unauthorized.getContentAsString()).contains("\"code\":\"AUTH_REQUIRED\"");

        MockHttpServletResponse forbidden = new MockHttpServletResponse();
        writer.forbidden(forbidden);
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(forbidden.getContentAsString()).contains("\"code\":\"ACCESS_DENIED\"");
    }
}

package com.shopflash.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import com.shopflash.common.web.ErrorBody;

@Component
public class SecurityErrorWriter {
    private final ObjectMapper json;

    public SecurityErrorWriter(ObjectMapper json) {
        this.json = json;
    }

    public void unauthorized(HttpServletResponse response) throws IOException {
        write(response, 401, "AUTH_REQUIRED", "Cần đăng nhập bằng tài khoản hợp lệ");
    }

    public void forbidden(HttpServletResponse response) throws IOException {
        write(response, 403, "ACCESS_DENIED", "Tài khoản không có quyền thực hiện thao tác này");
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(json.writeValueAsBytes(ErrorBody.of(code, message, null)));
    }
}

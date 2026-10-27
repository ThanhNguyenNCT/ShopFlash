package com.shopflash.common.security;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank @Size(max = 150) String fullName,
                              @NotBlank @Email @Size(max = 255) String email,
                              @Size(max = 30) String phone,
                              @NotBlank String password) {
    public RegisterRequest {
        if (fullName != null) fullName = fullName.strip();
        if (email != null) email = email.strip();
        if (phone != null) phone = phone.strip();
    }

    @JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("Trường không được phép: " + field);
    }
}

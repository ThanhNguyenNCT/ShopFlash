package com.shopflash.modules.storage.dto.request;

import com.shopflash.modules.storage.model.CategoryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body tạo hoặc cập nhật Category. Slug để trống sẽ được tạo từ name. */
public record CategorySaveRequest(
        @NotBlank(message = "Tên danh mục là bắt buộc") @Size(max = 150, message = "Tên danh mục tối đa 150 ký tự") String name,
        @Size(max = 180, message = "Slug tối đa 180 ký tự") String slug,
        @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự") String description,
        CategoryStatus status
) {
}

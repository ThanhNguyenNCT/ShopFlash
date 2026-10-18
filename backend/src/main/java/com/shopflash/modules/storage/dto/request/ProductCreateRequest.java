package com.shopflash.modules.storage.dto.request;

import com.shopflash.modules.storage.model.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body tạo Product và dòng tồn kho ban đầu. */
public record ProductCreateRequest(
        @NotNull(message = "Danh mục là bắt buộc") @Positive(message = "Danh mục không hợp lệ") Long categoryId,
        @NotBlank(message = "SKU là bắt buộc") @Size(max = 80, message = "SKU tối đa 80 ký tự") String sku,
        @NotBlank(message = "Tên sản phẩm là bắt buộc") @Size(max = 255, message = "Tên sản phẩm tối đa 255 ký tự") String name,
        @Size(max = 300, message = "Slug tối đa 300 ký tự") String slug,
        @Size(max = 10000, message = "Mô tả tối đa 10000 ký tự") String description,
        @NotNull(message = "Giá là bắt buộc") @DecimalMin(value = "0", message = "Giá phải lớn hơn hoặc bằng 0") BigDecimal basePrice,
        @Size(max = 500, message = "URL ảnh tối đa 500 ký tự") String thumbnailUrl,
        ProductStatus status,
        @NotNull(message = "Tồn kho ban đầu là bắt buộc") @DecimalMin(value = "0", message = "Tồn kho phải lớn hơn hoặc bằng 0") Long initialOnHandQuantity
) {
}

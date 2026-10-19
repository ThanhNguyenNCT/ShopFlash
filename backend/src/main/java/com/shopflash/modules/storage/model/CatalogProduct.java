package com.shopflash.modules.storage.model;

/** Product cùng Category và Inventory, dùng cho màn quản trị và Redis projection. */
public record CatalogProduct(
        Product product,
        String categoryName,
        CategoryStatus categoryStatus,
        ProductInventory inventory
) {
    public boolean inStock() {
        return product.status() == ProductStatus.ACTIVE
                && categoryStatus == CategoryStatus.ACTIVE
                && inventory.normalAvailableQuantity() > 0;
    }
}

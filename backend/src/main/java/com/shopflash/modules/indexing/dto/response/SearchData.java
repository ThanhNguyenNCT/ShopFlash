package com.shopflash.modules.indexing.dto.response;

import java.util.List;

public record SearchData(List<ProductItem> items, PageInfo page) {
}

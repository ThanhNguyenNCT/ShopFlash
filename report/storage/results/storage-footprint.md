# Storage Lab snapshot

Measured at: 2026-10-19T00:03:40+07:00
Number of runs: 1

| Measurement scope | Latest snapshot value |
|---|---:|
| Oracle Category | 100 |
| Oracle Product | 1,000 |
| Redis product document | 1,000 |
| Projection in sync | Yes |
| Oracle allocated bytes (table + index catalog) | 1,245,184 B |
| Redis product document key bytes | 575,000 B |

## Oracle segments in the latest snapshot

| Segment | Type | Bytes |
|---|---|---:|
| IX_PRODUCTS_CATEGORY_STATUS | INDEX | 65,536 |
| IX_PRODUCTS_NAME_UPPER | INDEX | 131,072 |
| IX_PRODUCTS_PRICE | INDEX | 65,536 |
| PK_CATEGORIES | INDEX | 65,536 |
| PK_PRODUCTS | INDEX | 65,536 |
| PK_PRODUCT_INVENTORY | INDEX | 65,536 |
| UK_CATEGORIES_SLUG | INDEX | 65,536 |
| UK_PRODUCTS_SKU | INDEX | 65,536 |
| UK_PRODUCTS_SLUG | INDEX | 131,072 |
| SYS_IL0000073136C00006$$ | LOBINDEX | 65,536 |
| CATEGORIES | TABLE | 65,536 |
| PRODUCTS | TABLE | 327,680 |
| PRODUCT_INVENTORY | TABLE | 65,536 |

Note: Oracle is the source data (Category, Product, Inventory, and indexes); Redis measures only product projection keys. These values are not the total footprint of either DBMS.

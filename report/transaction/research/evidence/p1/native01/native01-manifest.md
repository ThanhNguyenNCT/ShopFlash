# Manifest nghiên cứu native01

| Đối tượng | ID/giá trị |
|---|---|
| user | `10528` |
| category | `10530` |
| cart | `10520` |
| product 1/2/3 | `12941`, `12942`, `12943` |
| Order đang tồn tại | `[]`; fixture đã cleanup cuối P4 |
| Order lịch sử | `28441` — A1, code `TXRESEARCH_NATIVE01_A1C`, đã reset sau case |
| Order tạm trong transaction | `28461` — D1, chưa commit và đã full rollback |
| Redis key list | `native01-redis-keys.txt` |
| Redis Order key | `txresearch:native01:order` — dành cho A4/D2, đã thêm vào key list trước khi chạy |

Nguồn: `native01-oracle-setup.txt`, `native01-redis-setup.txt`. Cập nhật Order ID và key mới ngay khi case tạo ra; cleanup chỉ dùng ID/key trong manifest.

Cleanup cuối: Oracle [preview](../../p4/native01-final-oracle-preview.txt) → [cleanup](../../p4/native01-final-oracle-cleanup.txt) → [verify](../../p4/native01-final-oracle-verify.txt) (`VERIFY_ABSENT=PASS`); Redis [preview](../../p4/native01-final-redis-preview.txt) → [cleanup](../../p4/native01-final-redis-cleanup.txt) → [verify](../../p4/native01-final-redis-verify.txt) (mọi key `none`, không còn key cùng run). Muốn chạy lại phải setup run mới hoặc tái tạo fixture có kiểm soát.

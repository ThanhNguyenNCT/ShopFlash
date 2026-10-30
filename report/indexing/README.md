# 3.2 Indexing: thí nghiệm và số đo (TV2)

Chủ đề Indexing của module `indexing` (F3 tìm kiếm, F4 lọc nâng cao). Thư mục này chứa ghi chú lý thuyết, script thí nghiệm trên Oracle và Redis, số liệu thô và bảng kết quả.

## Cấu trúc

```text
report/indexing/
├── README.md            file này: cấu trúc, cách chạy, trạng thái
├── SECTION_INDEXING.md  **mục 3.2 của báo cáo** (lý thuyết, Oracle, Redis, so sánh, kết luận, tài liệu tham khảo)
├── ly-thuyet.md         ghi chú 8 bài lý thuyết (nguyên liệu cho phần Lý thuyết của 3.2)
├── ket-qua.md           bảng kết quả và nhận xét từng thí nghiệm (nguyên liệu cho phần Demo/So sánh)
├── ket-luan.md          bảng so sánh tổng hợp và quyết định cho F3/F4
├── oracle/              chạy trong container Oracle bằng run.sh
│   ├── run.sh             chạy 1 file SQL bằng user SHOP
│   ├── 00_setup.sql       tạo bảng thí nghiệm IDXLAB_PRODUCTS (200.000 dòng)
│   ├── e1_sku.sql         E1: tìm theo SKU
│   ├── e2_name.sql        E2: tìm từ trong tên
│   ├── e3_filter.sql      E3: lọc + sắp xếp + phân trang
│   ├── e4_write.sql       E4: chi phí ghi
│   ├── e6_freshness.sql   E6: index cập nhật lúc nào
│   ├── 99_cleanup.sql     xóa bảng thí nghiệm
│   └── lib/               phần dùng chung (đo thời gian, đo dung lượng Oracle Text); không chạy riêng
├── redis/               chạy trong container Redis bằng sh
│   ├── 00_setup.sh        nạp 200.000 document idxlab:product:* (cùng dữ liệu với Oracle)
│   ├── e1_sku.sh          E1: tìm theo SKU
│   ├── e2_name.sh         E2: tìm từ trong tên
│   ├── e3_filter.sh       E3: lọc + sắp xếp + phân trang
│   ├── e4_write.sh        E4: chi phí ghi
│   ├── e6_freshness.sh    E6: index cập nhật lúc nào
│   ├── 99_cleanup.sh      xóa mọi key idxlab:* và index idx:idxlab-*
│   ├── lib/common.sh      hàm dùng chung (đo thời gian, FT.INFO, bộ nhớ)
│   └── lua/               script Lua chạy bên trong Redis
└── results/             kết quả thô của từng lần chạy
    ├── oracle/            00-setup.txt, e1-sku.txt, e2-name.txt, ...
    └── redis/             00-setup.txt, e1-sku.txt, e2-name.txt, ...
```

Dữ liệu thí nghiệm nằm riêng, không đụng dữ liệu thật của nhóm: bảng `IDXLAB_PRODUCTS` (Oracle) và prefix `idxlab:` / index `idx:idxlab-*` (Redis). Index thí nghiệm được tạo rồi xóa trong từng script, nên không làm lệch số đo footprint của TV1.

## Thí nghiệm

| # | Câu hỏi | Oracle | Redis | Trạng thái |
|---|---|---|---|---|
| E1 | Tìm theo SKU | Full Scan → B-tree unique | `SCAN` → index tự làm → RediSearch TAG | Xong |
| E2 | Tìm từ trong tên (F3) | `LIKE '%từ%'` → Oracle Text CONTEXT → CTXCAT | `SCAN` → RediSearch TEXT | Xong |
| E3 | Lọc + sắp xếp + phân trang (F4) | Không index → 2 index đơn → index ghép đúng thứ tự → index ghép sai thứ tự | `SCAN` → Sorted Set tự làm → RediSearch | Xong |
| E4 | Chi phí ghi | Thêm 10.000 / sửa giá 10.000 với: khóa chính → +SKU → +3 B-tree → +CTXCAT / +CONTEXT | Cùng lượng ghi với: không index → tự làm 1 → tự làm 3 → RediSearch 1 trường → RediSearch đầy đủ | Xong |
| E5 | Dung lượng | `USER_SEGMENTS` | `FT.INFO`, `INFO memory` | Đo kèm E1–E3 |
| E6 | Thời điểm cập nhật index | B-tree theo transaction; CONTEXT chưa đồng bộ / `SYNC (ON COMMIT)` | RediSearch index nền khi tạo, thấy ngay khi ghi; index tự làm lệch khi sập giữa chừng, MULTI/EXEC không hoàn tác | Xong |

Cách đo chung: đo bên trong DB (Oracle: vòng lặp PL/SQL; Redis: script Lua, thời gian lấy từ `INFO commandstats`). 1 lần khởi động bỏ qua + 5 lần đo, báo trung bình ± độ lệch chuẩn. Chi tiết và lý do ở `ket-qua.md`.

## Cách chạy (Windows PowerShell, ở thư mục gốc repo)

Cần stack đang chạy (`scripts\dev.ps1`). Lệnh không chứa dấu ngoặc kép bên trong vì Windows PowerShell 5.1 làm rơi chúng khi truyền cho `docker`.

**1. Chép script vào container** (làm lại mỗi khi script thay đổi):

```powershell
docker cp report/indexing/oracle/. shopflash-oracle-1:/tmp/indexing/
docker cp report/indexing/redis/. shopflash-redis-1:/tmp/indexing-redis/
```

**2. Tạo dữ liệu thí nghiệm** (một lần):

```powershell
docker compose exec -T oracle bash /tmp/indexing/run.sh 00_setup.sql | Tee-Object -FilePath report\indexing\results\oracle\00-setup.txt
docker compose exec -T redis sh /tmp/indexing-redis/00_setup.sh | Tee-Object -FilePath report\indexing\results\redis\00-setup.txt
```

**3. Chạy một thí nghiệm**, ví dụ E2:

```powershell
docker compose exec -T oracle bash /tmp/indexing/run.sh e2_name.sql | Tee-Object -FilePath report\indexing\results\oracle\e2-name.txt
docker compose exec -T redis sh /tmp/indexing-redis/e2_name.sh | Tee-Object -FilePath report\indexing\results\redis\e2-name.txt
```

Trong lúc đo không thao tác trên web ShopFlash và không chạy việc nặng khác. `Tee-Object` của PowerShell 5.1 lưu file dạng UTF-16; đổi sang UTF-8 trước khi commit.

**4. Dọn dẹp** khi làm xong toàn bộ:

```powershell
docker compose exec -T oracle bash /tmp/indexing/run.sh 99_cleanup.sql
docker compose exec -T redis sh /tmp/indexing-redis/99_cleanup.sh
```

## Ảnh nên chụp cho báo cáo và buổi thuyết trình

Lưu vào `report/indexing/images/` (tạo thư mục khi chụp), đặt tên `3.2-xx-*.png` như cách TV3 làm:

1. `3.2-01-tim-kiem-redis.png`: trang `/search?q=tai`, thấy nhãn **RediSearch**.
2. `3.2-02-loc-oracle.png`: trang `/search` lọc danh mục + giá, thấy nhãn **Oracle**.
3. `3.2-03-lab-so-sanh.png`: Indexing Lab, thẻ 2 "So sánh hai nguồn", báo hai nguồn cho cùng kết quả.
4. `3.2-04-lab-co-khong-index.png`: Indexing Lab, thẻ 3, bảng 5 cách chạy.
5. `3.2-05-lab-ke-hoach.png`: Indexing Lab, thẻ 4, kế hoạch Oracle có `INDEX RANGE SCAN` (mẫu "Danh mục + giá").
6. `3.2-06-lab-dung-luong.png`: Indexing Lab, thẻ 5.

Ảnh kế hoạch thực thi của E1–E3 trên 200.000 sản phẩm lấy từ các file trong `results/oracle/`.

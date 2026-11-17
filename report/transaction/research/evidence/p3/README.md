# P3 — Concurrency và giao dịch nhiều bước

P3 gồm bảy thí nghiệm B1–B5, D1–D2. Với Oracle, timeline ghi rõ session A/B, thời điểm lấy khóa, lúc chờ, lỗi DBMS và lệnh rollback. Với Redis, các chuỗi `WATCH`/`MULTI`/`EXEC` luôn ở một kết nối cho client đang giao dịch; client cạnh tranh dùng kết nối khác.

| Case | Kết quả cần nhớ | Hồ sơ và output |
|---|---|---|
| B1 | `READ COMMITTED` không đọc bẩn; SELECT sau commit thấy bản mới | [B1](B1/README.md) |
| B2 | SELECT thường: hai quyết định ưu đãi; FOR UPDATE: một. Tồn cuối hai nhánh đều 98 | [B2 hiện hành](B2/README.md) |
| B3 | `WAIT 2` trả timeout; vòng chờ hai row trả `ORA-00060`; cả hai session được rollback | [B3](B3/README.md) |
| B4 | Hai HINCRBY giữ đủ hai lần giảm; WATCH/HSET conflict và retry đọc lại | [B4 hiện hành](B4/README.md) |
| B5 | Lua cho đúng hai trong bốn client giữ quota 2 | [B5](B5/README.md) |
| D1 | Checkout Oracle lỗi sau ghi một phần; full rollback phục hồi Order/Items/Cart/Inventory | [D1](D1/README.md) |
| D2 | Lua tiền kiểm hàng và quota trước ghi; nhánh lỗi không tạo ghi dở | [D2](D2/README.md) |

Oracle 26ai trong lần chạy này trả `ORA-00054` cùng `ORA-40097` cho timeout `FOR UPDATE WAIT 2`, và `ORA-00060` cho deadlock. [Timeline B3](B3/native01-B3-timeline.txt) cho thấy thứ tự tạo vòng chờ và việc rollback cả hai session. Sau đó session A lấy lại P1/P2 bằng `WAIT 2`, nên không còn khóa fixture bị giữ. Oracle và Redis được reset về baseline trước cleanup cuối P4. Không có blocker P3.

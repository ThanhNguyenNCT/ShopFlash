# Gợi ý công cụ đo theo chủ đề

Bảng này chỉ để tham khảo. Mỗi người tùy chủ đề và thứ mình muốn đo mà chọn công cụ phù hợp.

| Chủ đề | Oracle | Redis | Đo để cho thấy điều gì |
|---|---|---|---|
| Data storage | `DBA_SEGMENTS` | `MEMORY USAGE`, `INFO memory` | Cùng một lượng dữ liệu thì mỗi DB tốn bao nhiêu chỗ, và tốn ở đâu: Oracle lưu trên đĩa theo block/extent, Redis giữ toàn bộ trên RAM. Từ đó thấy dữ liệu lớn, lâu dài nên để Oracle, dữ liệu nhỏ, tạm thời hợp với Redis |
| Indexing | `SET TIMING`, `AUTOTRACE` | `memtier_benchmark` với `FT.SEARCH`, `ZRANGEBYSCORE` | Index giúp truy vấn nhanh lên bao nhiêu lần, và phải trả giá gì: ghi chậm hơn, tốn thêm dung lượng. Oracle tự duy trì index. Redis phải tự dựng index bằng ZSet/Set hoặc dùng RediSearch |
| Query processing | `DBMS_XPLAN`, `V$SQL` | `FT.PROFILE`, so sánh có/không pipeline bằng `redis-benchmark` | Oracle có bộ tối ưu tự chọn cách chạy (đổi thống kê là kế hoạch đổi theo). Redis không có bộ tối ưu nên lập trình viên phải tự giảm số lượt gọi qua mạng bằng pipeline hoặc Lua. Từ đó thấy truy vấn phức tạp, báo cáo hợp với Oracle hơn |
| Transaction | YCSB, HammerDB | YCSB | Cái giá về hiệu năng của việc đảm bảo ACID: Oracle chậm hơn nhưng lỗi thì rollback toàn bộ, Redis nhanh nhưng `MULTI/EXEC` không rollback. Phần cốt lõi của chủ đề này là demo hành vi khi lỗi; số đo tốc độ chỉ là phần phụ |
| Concurrency | k6 qua API flash sale, Swingbench | k6 qua API flash sale, `memtier_benchmark` | Khi nhiều người tranh nhau mua cùng lúc: có bị bán vượt số suất không, chịu được bao nhiêu request/giây, độ trễ p95 tăng ra sao. So sánh cơ chế khóa dòng của Oracle với thao tác nguyên tử, đơn luồng của Redis |
| Backup & recovery | Đo thời gian `expdp`/`impdp` | `redis-benchmark` khi bật AOF `always` / `everysec` / tắt AOF | Đánh đổi giữa an toàn dữ liệu và tốc độ: sao lưu và khôi phục mất bao lâu, sập máy thì mất tối đa bao nhiêu dữ liệu, cấu hình ghi log an toàn hơn làm throughput giảm bao nhiêu |

**Lưu ý dòng Transaction:** YCSB và HammerDB chỉ đo tốc độ, nên không cho thấy được điểm chính của chủ đề này là rollback. Nên chuẩn bị thêm một demo cố ý gây lỗi giữa chừng trên cả hai DB.

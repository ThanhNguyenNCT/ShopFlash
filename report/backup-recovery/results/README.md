# Kết quả chạy Backup & Recovery

Thư mục này lưu bằng chứng ở mức báo cáo cho từng lượt chạy kịch bản backup và recovery. Artifact nhị phân gốc (Oracle `.dmp` và Redis `.rdb`) không được đặt ở đây và không commit vào Git; chúng nằm tại `../../backups/<backup-id>/` ở thư mục gốc của project.

## Quy ước cho các lượt chạy sau

Mỗi lượt demo PowerShell dùng cùng một `backupId` và chỉ tạo hai tệp dưới `run-<n>/`:

| Tệp | Thời điểm tạo | Nội dung |
|---|---|---|
| `state.json` | Từ action đầu tiên, được ghi đè ở mỗi bước | Timing backup; baseline; chứng cứ mất dữ liệu; hash, checkpoint và thời gian kỹ thuật của Oracle/Redis restore |
| `summary.md` | Sau `Summarize` | Kết luận ngắn để dùng cho báo cáo |

Thời gian khôi phục trong `state.json` và summary là thời gian kỹ thuật của script, không gồm thao tác thủ công, nên không gọi là RTO. Hướng dẫn Bash giữ checkpoint riêng trong `bash-run-<n>/.work/` để người chạy dùng `diff`, còn thư mục chạy chỉ hiển thị `summary.md`.

Một lượt recovery chỉ được kết luận thành công khi số liệu Oracle (`CATEGORIES`, `PRODUCTS`, `PRODUCT_INVENTORY`), tổng Redis key và số document Redis (`product:*`) khớp với manifest. Các Product mẫu chỉ được đối chiếu ở Oracle. Không dùng lại kết quả của lượt này để khẳng định cho một backup khác.

## Lưu ý an toàn

- Dừng backend và frontend trước khi chạy bước giả lập mất dữ liệu hoặc khôi phục.
- Không commit dump, RDB, log Data Pump hoặc mật khẩu.
- `RestoreRedisLive` chỉ dùng ngay sau khi giả lập mất dữ liệu: script chỉ chạy khi Redis rỗng, archive persistence cũ trước khi thay RDB và không nhận đường dẫn/command tùy ý.

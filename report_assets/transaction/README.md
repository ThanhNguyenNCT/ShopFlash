# Ba biểu đồ cho Mục 3.3

Chỉ giữ ba hình thay thế các placeholder ban đầu trong `3.3_Danh_gia_hieu_nang_va_lua_chon_DBMS.md`:

| Số hình | File SVG / PNG cùng tên | Nguồn dữ liệu |
|---|---|---|
| 3.1 | `hinh-3-1-database-p95` | `database-summary.csv`, cột `p95_ms` |
| 3.2 | `hinh-3-2-database-tps` | `database-summary.csv`, cột `throughput_success_per_second` |
| 3.3 | `hinh-3-3-http-p95` | `http-summary.csv`, cột `p95_ms` của `checkout.place` và `full_flow` |

Các CSV thuộc `../../report/transaction/results/summary-20261027/`. [chart-data.csv](chart-data.csv) lưu đúng 24 giá trị được vẽ. [manifest.json](manifest.json) ghi nguồn và SHA-256; [data-verification.json](data-verification.json) ghi kết quả tính lại từ raw CSV/runs JSONL và compressed k6 JSON, đối chiếu summary CSV/JSON.

p95 tính gộp từ 1.000 mẫu thành công mỗi tổ hợp, loại warm-up. TPS dùng tổng số thành công / tổng wall time năm round. HTTP là phép đo ứng dụng dùng Oracle, không phải so sánh Oracle–Redis. Các biểu đồ có trục bắt đầu từ 0, đơn vị và nhãn Cart Size/Concurrency.

Markdown dùng SVG vector, font chuyển thành path để giữ dấu tiếng Việt. PNG tương ứng có độ phân giải 300 dpi để sử dụng khi công cụ xuất PDF không hỗ trợ SVG.

Tạo lại hình bằng Python có Matplotlib:

```bash
python report_assets/transaction/generate_assets.py
```

Mục 3.1 và 3.2 đã được khôi phục nguyên bản, không chèn thêm sơ đồ. Các đoạn trích và nội dung chính của Mục 3.3 giữ nguyên.

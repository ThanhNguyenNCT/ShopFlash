# P1 — Fixture riêng và kiểm tra cleanup

P1 chuẩn bị dữ liệu để các case có thể chạy trên schema ShopFlash mà không sửa dữ liệu của thành viên khác. Oracle dùng user, category, ba product/inventory và một cart riêng; Redis dùng các key `txresearch:<runId>:*`. Mỗi lần setup in ra ID/key thật để lưu vào manifest. Các script reset và cleanup chỉ nhận đúng những ID/key đó.

## Bố trí bằng chứng

```text
p1/
├── README.md                Tổng hợp P1
├── trial1/                  Vòng thử setup → reset → cleanup đầu tiên
├── trial2/                  Vòng thử lặp lại với identity mới
└── native01/                Manifest và output setup của run 14 case
```

Hai vòng `trial1` và `trial2` chỉ kiểm tra khả năng tái lập fixture, không phải thí nghiệm A1–D2. [Manifest vòng 1](trial1/p1trial01-manifest.md) và [manifest vòng 2](trial2/p1trial01-cycle2-manifest.md) ghi ID khác nhau dù cùng tên run được tạo lại **sau khi cleanup hoàn tất**. [Manifest `native01`](native01/native01-manifest.md) và [danh sách key](native01/native01-redis-keys.txt) thuộc bộ 14 case đã chạy sau đó; run này cũng đã cleanup cuối P4.

## Cách kiểm tra an toàn

1. `setup.sql` tạo đúng một user, một category, ba Product/Inventory và một Cart có ba dòng. `redis/setup.sh` tạo năm Hash đầu kỳ. Gọi lại setup trên trạng thái đầu kỳ trả `EXISTS` với cùng ID, không sinh thêm fixture.
2. `reset.sql` và `redis/reset.sh` đưa fixture về tồn 100, Cart ba dòng và quota 2. Trước mỗi reset/cleanup, `cleanup-preview.sql` hoặc `redis/preview.sh` cho thấy phạm vi xóa; người chạy phải đối chiếu với manifest.
3. `cleanup.sql` xóa Oracle theo thứ tự FK và chỉ commit khi mọi ID/tag khớp manifest. `redis/cleanup.sh` chỉ `DEL` key liệt kê trong file, không dùng wildcard để xóa. Sau mỗi vòng, `verify-absent.sql` trả `VERIFY_ABSENT=PASS`; Redis không còn key của run.

[Kết quả vòng 1](trial1/p1trial01-oracle-verify-absent.txt) và [vòng 2](trial2/p1trial01-cycle2-oracle-verify-absent.txt) đều không còn row Oracle. [Redis audit sau vòng 1](trial1/p1trial01-redis-audit-after-cleanup.txt) và [sau vòng 2](trial2/p1trial01-cycle2-redis-audit-after-cleanup.txt) ghi các prefix ứng dụng/nghiên cứu không bị thay đổi ngoài fixture. Không có blocker P1.

Cách gọi lệnh và cách xử lý khi chỉ một kho cleanup thành công nằm trong [runbook](../../README.md). Không tái dùng số ID của các vòng này cho run mới.

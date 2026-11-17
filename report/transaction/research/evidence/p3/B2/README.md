# B2 — READ COMMITTED: SELECT thường và SELECT FOR UPDATE

- Bản hiện hành: revision-20261109, run `rev1109b`, năm lượt correctness. [Tổng hợp và cleanup](../../revision-20261109/README.md).
- [Timeline lượt 1](../../revision-20261109/1-B2.txt); các lượt 2–5 có cùng tên tương ứng. SQL*Plus giữ hai connection A/B, autocommit OFF và READ COMMITTED.
- B2.1: A đọc 100/đủ ưu đãi, UPDATE tương đối chưa commit; B SELECT thường thấy committed 100/đủ ưu đãi rồi UPDATE chờ A. A COMMIT; B hoàn tất UPDATE/COMMIT. Assertion tồn=98, số quyết định ưu đãi=2.
- B2.2: reset 100. A SELECT FOR UPDATE đọc 100/đủ ưu đãi; B SELECT FOR UPDATE WAIT 30 chờ. A UPDATE/COMMIT; B lấy khóa, đọc 99/không ưu đãi, UPDATE/COMMIT. Assertion tồn=98, số quyết định ưu đãi=1.
- Timeline ghi `B_WAIT_CONFIRMED` trong từng nhánh, sau đó lệnh A COMMIT và phản hồi B. Thời điểm in phản hồi hai session có thể xen nhau do reader threads; không giả định thứ tự print A/B là thứ tự hoàn tất trong server.
- PL/SQL assertion kiểm tra read/decision/stock thực; parser tính tổng eligibility từ output DB. Mỗi nhánh khôi phục 100. B lấy lại khóa rồi ROLLBACK; snapshot độc lập sau từng lượt bằng baseline. Không timeout Oracle cố ý, không protected/overselling branch.
- UPDATE thường tự lấy row lock. FOR UPDATE thay đổi lúc khóa để quyết định nghiệp vụ dựa trên dữ liệu sau transaction trước; COMMIT xác nhận/cho phép nhìn thấy UPDATE đã thực hiện. Không tạo Order thật.

---

## Hồ sơ cũ — SUPERSEDED

Các kết quả dưới đây chỉ mô tả phiên bản cũ; không dùng để nghiệm thu code hiện hành. Mọi transcript cũ giữ nguyên để truy vết.

# B2 — Lost Update và bảo vệ tồn khả dụng

- Trạng thái: **PASS**. Nhánh Lost Update được chạy lại ngày 2026-10-28 12:25 +07 với run `b2redo01`; nhánh bảo vệ được giữ nguyên trong script và chạy lại cùng B2.
- Fixture riêng: `USER_ID=10546`, `CATEGORY_ID=10548`, `CART_ID=10538`, P1=`12955`, P2=`12956`, P3=`12957`. [Setup](b2redo01-setup.txt) và [snapshot trước](b2redo01-before.txt) xác nhận P1 có `ON_HAND=100`, `RESERVED=0`, `VERSION=0`.
- Lệnh chạy: `python3 report/transaction/research/oracle/experiments/two-session.py B2 12955 12956`. Hai SQL*Plus session A/B duy trì kết nối riêng trong toàn bộ thí nghiệm. [Timeline mới](b2redo01-B2-timeline.txt) là bằng chứng chính; [timeline `native01` cũ](native01-B2-timeline.txt) chỉ lưu lịch sử trước khi sửa nhánh Lost Update.

## Đối chứng Lost Update

1. A và B cùng đọc P1=100, mỗi session lưu kết quả vào bind `:old_stock` của chính kết nối đó. `PRINT old_stock` trong timeline xác nhận cả hai biến bằng 100.
2. A chạy `UPDATE ... SET on_hand_quantity = :old_stock - 1` và giữ transaction chưa commit. B gửi cùng câu `UPDATE` trên P1. B không nhận kết quả trong 1,5 giây khi A giữ khóa; timeline ghi `B_WAIT_CONFIRMED`.
3. A gửi `COMMIT`; B mới nhận `1 row updated`, rồi B `COMMIT`. SELECT từ A cho tồn cuối là **99**. Hai lần giảm từ tồn ban đầu 100 lẽ ra cho 98; lần ghi của B dùng giá trị cũ 100 nên ghi đè kết quả giảm của A. Đây là Lost Update ở mẫu đọc–tính–ghi, dù khóa dòng vẫn tuần tự hóa hai câu UPDATE. Dòng `B< 1 row updated` có thể được in trước `A< Commit complete` do hai luồng đọc output độc lập; lệnh `A> COMMIT` đã được gửi trước khi B trả kết quả.

## Nhánh bảo vệ và dọn dữ liệu

Nhánh bảo vệ của B2 không đổi: đặt P1=1; A giữ `FOR UPDATE`; B dùng `FOR UPDATE WAIT 2` và nhận `ORA-00054`/`ORA-40097`, sau đó `ROLLBACK`. A chạy conditional `UPDATE` và trừ tồn xuống 0; B chạy cùng câu sau đó được `0 rows updated`, không bán quá tồn. A khôi phục P1=100/version=0 rồi commit. [Snapshot sau](b2redo01-after.txt) giống hệt snapshot trước.

[Cleanup preview](b2redo01-cleanup-preview.txt) khớp đúng sáu ID fixture và không có Order. [Cleanup](b2redo01-cleanup.txt) đã commit; [hậu kiểm](b2redo01-verify-absent.txt) ghi `VERIFY_ABSENT=PASS` với mọi bảng fixture bằng 0. B2 chỉ mô phỏng hai lượt mua, không tạo Order thật.

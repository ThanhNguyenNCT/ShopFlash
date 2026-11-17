# P2 — Atomicity và consistency

P2 gồm bảy thí nghiệm A1–A5, C1–C2. Chúng trả lời hai câu hỏi: khi một bước lỗi thì những ghi nào còn lại, và DBMS tự kiểm tra được những quy tắc dữ liệu nào. Bản gốc chạy thật trên fixture `native01`; C2 được thay bằng revision-20261109; sau mỗi case có ghi, fixture được reset về trạng thái đầu kỳ trước case tiếp theo.

| Case | Kết quả cần nhớ | Hồ sơ và output |
|---|---|---|
| A1 | Oracle `COMMIT` làm Order hiện với session khác; `ROLLBACK` bỏ ghi chưa commit | [A1](A1/README.md) |
| A2 | Oracle tự trả ba mã lỗi UNIQUE/FK/CHECK từ constraint ShopFlash | [A2](A2/README.md) |
| A3 | Statement lỗi, `ROLLBACK TO` và full `ROLLBACK` có phạm vi khác nhau | [A3](A3/README.md) |
| A4 | Redis `EXEC` chạy hàng đợi; `DISCARD` bỏ hàng đợi chưa chạy | [A4](A4/README.md) |
| A5 | Lỗi runtime trong `EXEC`/Lua giữ ghi đã chạy; lỗi queue-time gây `EXECABORT` | [A5](A5/README.md) |
| C1 | Oracle constraint bảo vệ quan hệ/giá trị row; Checkout vẫn cần validation | [C1](C1/README.md) |
| C2 | Guarded EVAL từ chối trước ghi; snapshot lỗi không đổi, valid giữ invariant | [C2 hiện hành](C2/README.md) |

Mỗi thư mục case có `README.md` diễn giải kết quả và các transcript `.txt` ngay bên cạnh. Lỗi cố ý được giữ nguyên trong output để người đọc kiểm chứng mã lỗi thật. Sau P2, Oracle và Redis fixture trở về baseline cho P3. Đây là bằng chứng cơ chế, không phải benchmark thời gian. Không có blocker P2.

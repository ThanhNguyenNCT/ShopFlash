# B1 — READ COMMITTED giữa hai Oracle session

- Trạng thái: **PASS**, run `native01`, 2026-10-28 07:54:51 +07.
- Mục tiêu: B không đọc bẩn; hai SELECT trong transaction B có thể thấy hai phiên bản sau A commit.
- Setup: Inventory P1=100 (`native01-P3-before.txt`); `two-session.py B1 12941 12942` mở hai SQL*Plus session cố định, `SET TRANSACTION ISOLATION LEVEL READ COMMITTED`, autocommit OFF.
- Execute/observe: timeline SQL và output gốc ở `native01-B1-timeline.txt`: B đọc 100; A UPDATE 99 nhưng chưa COMMIT, B vẫn đọc 100; A COMMIT, B đọc 99 trong cùng transaction; A khôi phục 100 rồi COMMIT.
- Expected/actual: `100 → 100 → 99`, đúng giả thuyết READ COMMITTED. B rollback/đóng session; `native01-B1-after.txt` giống snapshot đầu kỳ.
- Cleanup: khôi phục Inventory P1=100 trong session A, rollback và đóng cả hai session; không còn lock.
- Kết luận: Oracle không cho B thấy ghi chưa commit, nhưng mỗi statement READ COMMITTED lấy snapshot mới.

## Giải thích kết quả

Đọc [timeline hai session](native01-B1-timeline.txt) theo thứ tự B–A–B–A–B. Lần đầu B thấy tồn 100. A đổi còn 99 nhưng chưa `COMMIT`, nên lần đọc tiếp theo của B vẫn là 100: không có dirty read. Sau A `COMMIT`, B đọc lại và thấy 99, dù B vẫn ở transaction `READ COMMITTED`. Mỗi câu SELECT trong chế độ này có thể thấy bản đã commit mới hơn.

Hai kết nối SQL*Plus được giữ cố định suốt timeline; việc mở một kết nối mới cho từng bước sẽ không chứng minh được hành vi trong cùng transaction B. A phục hồi 100, hai session rollback/đóng và [snapshot độc lập](native01-B1-after.txt) bằng đầu kỳ.

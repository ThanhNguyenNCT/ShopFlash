# B3 — Timeout và deadlock Oracle

- Trạng thái: **PASS**, run `native01`, 2026-10-28 07:55:37 +07.
- Mục tiêu: phân biệt chờ khóa có hạn thời gian, deadlock, rollback statement lỗi và chủ động rollback transaction.
- Setup: P1/P2 là hai Inventory fixture riêng (`native01-P3-before.txt`); `two-session.py B3 12941 12942` mở hai SQL*Plus session cố định. Mọi `FOR UPDATE` có `WAIT 2/3/10`; helper đặt hạn 5/13 giây và tự đóng session nếu vượt hạn.
- Execute/observe: `native01-B3-timeline.txt` có mốc thời gian. Lượt timeout: A khóa P1, B `FOR UPDATE WAIT 2` cùng row, sau hai giây trả `ORA-00054` kèm `ORA-40097` ở Oracle 26ai; B và A đều chủ động ROLLBACK. Lượt deadlock: A khóa P1, B khóa P2, A chờ P2, B chờ P1; A nhận `ORA-00060` sau khoảng hai giây. Helper rollback A ngay sau lỗi để giải phóng khóa ban đầu, B lấy được P1 rồi chủ động rollback. A tiếp tục lấy cả P1/P2 `WAIT 2` thành công, chứng minh không còn khóa treo.
- Expected/actual: mã lỗi DBMS có thật, thời điểm chờ hữu hạn; `ORA-00060` hủy statement bị chọn làm nạn nhân, không thay cho lệnh ROLLBACK toàn transaction. `native01-B3-after.txt` giống snapshot đầu kỳ.
- Cleanup/cứu hộ: cả hai session gửi `ROLLBACK; EXIT`; helper terminate/kill SQL*Plus nếu quá hạn. Không cần dùng nhánh cứu hộ ở lần chạy này; hậu kiểm lấy khóa P1/P2 và snapshot đều thành công.
- Kết luận: Oracle báo lỗi timeout/deadlock ở statement chờ; session phải chủ động rollback để giải phóng mọi khóa còn giữ trước khi dọn fixture.

## Giải thích kết quả

[Timeline B3](native01-B3-timeline.txt) có hai lượt tách biệt. Lượt thứ nhất: A khóa P1, B yêu cầu P1 với `WAIT 2`; sau khoảng hai giây B nhận `ORA-00054` kèm `ORA-40097` trên Oracle 26ai. Lượt thứ hai tạo vòng chờ đúng thứ tự: A khóa P1, B khóa P2, A chờ P2, rồi B chờ P1. Oracle phát hiện vòng chờ và trả `ORA-00060` cho A.

Lỗi deadlock kết thúc câu chờ của A; nó không thay lệnh dọn transaction. Helper chủ động `ROLLBACK` A để nhả khóa P1; B lấy được P1 rồi cũng `ROLLBACK`. Sau đó A lấy lại cả P1/P2 bằng `WAIT 2`, chứng minh không còn khóa treo. Giới hạn chờ và nhánh đóng session quá hạn nằm trong runbook; lần chạy này không phải dùng cứu hộ. [Snapshot cuối](native01-B3-after.txt) bằng đầu kỳ.

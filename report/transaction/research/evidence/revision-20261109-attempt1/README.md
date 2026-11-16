# Lượt đầu — FAIL harness, đã cleanup

Run `rev1109`, ngày 09/11/2026. [1-B2.txt](1-B2.txt) ghi Oracle trả read/decision 100/1 cho cả hai session và stock=98, nhưng parser dùng regex `expected=\d/\d`, không nhận được `100/1`, nên assertion tổng eligibility thất bại. Đây là lỗi harness; lượt này không được tính PASS.

Đã sửa regex thành `expected=\d+/\d`, không sửa output cũ. Runner finally dọn cả hai DBMS; [oracle-verify.txt](oracle-verify.txt) có `VERIFY_ABSENT=PASS`, [redis-verify.txt](redis-verify.txt) có bảy key none và ALL_RUN_KEYS rỗng. P0 audit trong thư mục này thực hiện trước mọi ghi của cả đợt.

Lượt thay thế: [revision-20261109](../revision-20261109/README.md), run `rev1109b` mới.

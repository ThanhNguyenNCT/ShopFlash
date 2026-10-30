-- Module indexing (TV2): index phục vụ F3 tìm kiếm và F4 lọc sản phẩm.
-- Lý do và số đo: report/indexing/ket-luan.md (E2, E3).

-- F4 duyệt danh mục, lọc giá, sắp theo giá (E3: nhanh hơn RediSearch 10-43 lần).
-- Thứ tự cột: cột so sánh bằng (category_id, status) trước, cột khoảng/sắp xếp (base_price) sau.
-- product_id ở cuối để thứ tự ổn định khi trùng giá (phân trang không lặp) mà Oracle vẫn không phải sắp xếp lại.
-- Bắt đầu bằng category_id nên vẫn là index của khóa ngoại fk_products_category.
-- ix_products_category_status (V1) trở thành thừa; tạm giữ, bỏ khi nhóm đồng ý.
CREATE INDEX ix_products_cat_status_price ON products (category_id, status, base_price, product_id);

-- F3 dự phòng khi Redis không truy cập được: tìm từ trong tên bằng Oracle Text.
-- Oracle AI Database 26ai mặc định tự bảo trì index ở chế độ nền (kiểm tra mỗi 3 giây, E6),
-- nên không cần quyền CTX_DDL để đồng bộ.
CREATE INDEX ix_products_name_text ON products (name) INDEXTYPE IS CTXSYS.CONTEXT;

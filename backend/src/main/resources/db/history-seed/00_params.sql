-- Tham số sinh dữ liệu benchmark cho module query (F5/F6).
-- Đổi quy mô: chỉ sửa các giá trị dưới đây, ví dụ n_orders = 500000.
define n_categories      = 50
define n_products        = 10000
define n_customers       = 20000
define n_orders          = 100000
-- Khoảng thời gian created_at của đơn (giờ +07:00, tính cả ngày cuối).
define start_date        = 2025-11-01
define end_date          = 2026-10-18
-- Phần trăm sản phẩm không bao giờ được bán (cho báo cáo "sản phẩm chưa bán").
define pct_unsold        = 10
-- Phần trăm sản phẩm INACTIVE.
define pct_inactive      = 5
-- Khách VIP: pct_vip_customers % khách nhận thêm pct_vip_orders % số đơn.
define pct_vip_customers = 5
define pct_vip_orders    = 25
-- Độ lệch độ phổ biến sản phẩm: hạng = n_sold * u^skew (2 = top bán chạy nổi bật rõ).
define popularity_skew   = 2
-- Seed cho ORA_HASH (dùng làm "seed" ngẫu nhiên, xem 01_generate.sql).
define seed              = 42

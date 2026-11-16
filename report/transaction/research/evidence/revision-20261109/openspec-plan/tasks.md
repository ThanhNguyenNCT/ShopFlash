# Tasks

## 1. B2
- [x] 1.1 Thay riêng B2 bằng hai nhánh SELECT/FOR UPDATE, assertion DB và chờ có điều phối; chạy thật năm lần, lưu timeline/snapshot và cập nhật hồ sơ B2.

## 2. B4
- [x] 2.1 Thêm atomic HINCRBY, đổi WATCH sang tính HSET/retry trên connection A; chạy thật năm lần với assertion/snapshot và cập nhật hồ sơ B4.

## 3. C2
- [x] 3.1 Thay Lua và harness bằng guarded Checkout, tiền kiểm đầy đủ, case lỗi độc lập và valid; chạy năm lần snapshot/assertion, cập nhật hồ sơ C2.

## 4. Hậu kiểm chung
- [x] 4.1 Xác minh reset/cleanup, không khóa/key/row tồn đọng và file ngoài scope không đổi; đồng bộ README/index/báo cáo, giữ transcript cũ superseded; validate OpenSpec strict.

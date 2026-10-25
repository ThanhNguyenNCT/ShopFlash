-- Bổ sung phiên bản giỏ và bằng chứng nội dung yêu cầu đặt hàng cho F10/F7.
-- Giữ nguyên V1; dữ liệu giỏ/đơn hiện có vẫn hợp lệ sau migration.

ALTER TABLE carts ADD (
  version NUMBER(19) DEFAULT 0 NOT NULL
);

ALTER TABLE orders ADD (
  checkout_request_hash VARCHAR2(64)
);

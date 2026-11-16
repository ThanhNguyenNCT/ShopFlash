-- Concurrency Lab (báo cáo 3.5): bộ đếm riêng cho kịch bản "N người tranh M suất".
-- Lab chỉ ghi bảng này và key Redis cclab:item, không đụng dữ liệu bán hàng.
-- Dòng dữ liệu do API POST /api/admin/concurrency-lab/reset tạo hoặc đặt lại (MERGE).
CREATE TABLE cc_lab_items (
  lab_item_id NUMBER(19) NOT NULL,
  allocated   NUMBER(10) NOT NULL,
  sold        NUMBER(10) NOT NULL,
  version     NUMBER(19) DEFAULT 0 NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_cc_lab_items PRIMARY KEY (lab_item_id),
  CONSTRAINT ck_cc_lab_items_allocated CHECK (allocated > 0),
  -- Không thay được điều khiển đồng thời: mất cập nhật vẫn ghi ra giá trị hợp lệ (thí nghiệm E1).
  CONSTRAINT ck_cc_lab_items_sold CHECK (sold >= 0 AND sold <= allocated)
);

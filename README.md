# ShopFlash

Bài tập lớn môn Hệ quản trị Cơ sở dữ liệu (HK261, L01). Nhóm so sánh Oracle và Redis qua 6 chủ đề của môn học, minh họa bằng ShopFlash, một ứng dụng bán hàng có flash sale.

## Công nghệ

- Oracle Database 23ai Free
- Redis Stack 7.x
- Backend: Java Spring Boot
- Frontend: React + Vite + Ant Design

## Cấu trúc

```
shopflash/
├── backend/    Spring Boot, chia theo 6 module
├── frontend/   React, chia theo 6 module (giống backend)
├── db/         Script tạo CSDL Oracle và Redis
├── docs/       Tài liệu dự án
└── report/     Báo cáo môn học
```

## Module

Mỗi module ứng với một chủ đề. Một thành viên làm trọn module của mình, từ CSDL, backend, frontend đến tài liệu.

| Module | Chủ đề | Chức năng |
|---|---|---|
| `storage` | Data storage & management | Quản lý danh mục, quản lý sản phẩm |
| `indexing` | Indexing | Tìm kiếm, lọc sản phẩm |
| `query` | Query processing | Báo cáo doanh thu, top bán chạy |
| `transaction` | Transaction | Đặt hàng, lịch sử và hủy đơn |
| `concurrency` | Concurrency control | Flash sale, giỏ hàng |
| `backup` | Data backup and recovery | Sao lưu dữ liệu |

## Design system

Mở `frontend/design-system.html` bằng trình duyệt để xem màu, chữ và component. Code nằm ở `frontend/src/shared/design-system/`.

## Cài đặt và chạy

Đang cập nhật.

## Dành cho AI agent

Mỗi thư mục quan trọng có file `AGENTS.md` mô tả mục đích và quy ước (`CLAUDE.md` trỏ tới cùng nội dung cho Claude Code).

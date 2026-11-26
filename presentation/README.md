# Thuyết trình: So sánh Oracle vs Redis và ứng dụng vào ShopFlash

Slide web cho buổi báo cáo 30 phút của Nhóm 11. Bản đang chạy: https://oracle-vs-redis-nhom11.vercel.app

## Cấu trúc

- `web/`: slide web tĩnh, không cần build. Mở `web/index.html` bằng trình duyệt là chạy.
  - `web/js/deck.js`: bộ chuyển cảnh, thanh mục lục, chế độ in.
  - `web/parts/00-mo-dau.js` đến `08-ket-thuc.js`: mỗi file một phần của bài.
  - `web/css/deck.css`, `web/assets/`: giao diện và hình.
- `ghi-chu-thuyet-trinh.md`: ghi chú người nói theo số cảnh, kèm câu hỏi có thể gặp.

## Trình bày

Mũi tên trái phải hoặc Space để chuyển cảnh, phím 1 đến 7 để nhảy tới đầu phần, F để toàn màn hình.
Nội dung đều hiện sẵn, không cần bấm chuột. Hai cảnh sau "Cảm ơn" là cảnh dự phòng cho phần hỏi đáp.
Nút "Mở demo" mở app ShopFlash ở `http://localhost:5173`, nên phải chạy app trên máy trình bày.

## Sửa slide

Phần 2, 3, 5, 6, 7 lấy từ bản Canva của nhóm: giữ nguyên câu chữ, chỉ đổi cách trình bày.
Sau khi sửa, tăng số `?v=` trong `web/index.html` để trình duyệt không dùng bản cũ.

## Xuất PDF

```bash
cd presentation/web
./export-pdf.sh            # ghi ra presentation/Oracle-vs-Redis-Nhom11.pdf
```

Cần Google Chrome và ImageMagick (`convert`). Không in bằng Ctrl+P: Chrome co khung 16:9 và cắt mất nội dung.

## Deploy

Project Vercel `oracle-vs-redis-nhom11` nối với repo này, thư mục gốc là `presentation/web`.
Merge vào `main` thì web tự cập nhật; commit không đổi gì trong `presentation/web` thì Vercel bỏ qua.

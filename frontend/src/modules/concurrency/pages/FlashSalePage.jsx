import ComingSoon from '@/shared/components/ComingSoon';

// F9 Flash sale (TV5). PLACEHOLDER: thay toàn bộ file này bằng màn hình thật, giữ export default.
// Route và menu đã khai báo trong ../routes.jsx.
export default function FlashSalePage() {
  return (
    <ComingSoon
      feature="F9"
      title="Flash sale"
      owner="TV5"
      module="concurrency"
      file="frontend/src/modules/concurrency/pages/FlashSalePage.jsx"
      description="Chương trình flash sale đang chạy: đồng hồ đếm ngược, suất còn lại, mua giới hạn khi nhiều người tranh nhau."
      todo={[
        'FlashCountdown cho chương trình đang chạy; lưới ProductCard có sold/quota (StockBar), giá sale và giá gốc.',
        'Nút "Mua ngay" giữ suất: xử lý nhiều người bấm cùng lúc (Redis giữ suất, giới hạn mỗi người), báo rõ "Hết suất" hoặc "Bạn đã mua đủ số lượng cho phép".',
        'Cập nhật số suất còn lại định kỳ mà không giật trang; chương trình chưa bắt đầu hoặc đã kết thúc có trạng thái riêng.',
        'Giữ suất thành công thì chuyển sang /checkout (module transaction) trước khi suất hết hạn.',
        'Chưa đăng nhập mà bấm mua: chuyển tới /login?next=/flash-sale (dùng useAuth của shared/auth).',
      ]}
      api={[
        'GET /api/flash-sales/active',
        'POST /api/flash-sales/{flashSaleId}/items/{itemId}/reservations',
      ]}
    />
  );
}

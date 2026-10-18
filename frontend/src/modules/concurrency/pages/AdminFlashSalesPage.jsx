import ComingSoon from '@/shared/components/ComingSoon';

// F9 Quản lý flash sale (TV5). PLACEHOLDER: thay toàn bộ file này bằng màn hình thật, giữ export default.
// Route và menu đã khai báo trong ../routes.jsx.
export default function AdminFlashSalesPage() {
  return (
    <ComingSoon
      feature="F9"
      title="Quản lý flash sale"
      owner="TV5"
      module="concurrency"
      file="frontend/src/modules/concurrency/pages/AdminFlashSalesPage.jsx"
      description="Admin tạo và cập nhật chương trình flash sale: thời gian, policy, sản phẩm, giá sale, quota."
      todo={[
        'Bảng chương trình (FLASH_SALES): tên, thời gian bắt đầu và kết thúc, trạng thái; phân trang.',
        'Form tạo/sửa: thời gian, policy (FLASH_SALE_CONFIGS: giới hạn mỗi người, thời gian giữ suất), chọn sản phẩm, giá sale, quota (FLASH_SALE_ITEMS).',
        'Kiểm tra dữ liệu: giá sale nhỏ hơn giá gốc, quota lớn hơn 0 và không vượt tồn kho, giờ kết thúc sau giờ bắt đầu.',
        'Hỏi xác nhận trước khi hủy hoặc xóa chương trình; ghi rõ quy tắc sửa khi chương trình đang chạy.',
        'Theo dõi suất đã bán và còn lại của chương trình đang chạy (StockBar).',
      ]}
      api={[
        'GET /api/admin/flash-sales?page=&size=',
        'POST /api/admin/flash-sales',
        'PUT /api/admin/flash-sales/{id}',
        'PUT /api/admin/flash-sales/{id}/items',
      ]}
    />
  );
}

import ComingSoon from '@/shared/components/ComingSoon';

// F1 Quản lý danh mục (TV1). PLACEHOLDER: thay toàn bộ file này bằng màn hình thật, giữ export default.
// Route và menu đã khai báo trong ../routes.jsx.
export default function AdminCategoriesPage() {
  return (
    <ComingSoon
      feature="F1"
      title="Quản lý danh mục"
      owner="TV1"
      module="storage"
      file="frontend/src/modules/storage/pages/AdminCategoriesPage.jsx"
      description="Tạo, xem, cập nhật và quản lý trạng thái danh mục sản phẩm."
      todo={[
        'Bảng danh mục (CATEGORIES): tên, trạng thái, số sản phẩm; phân trang, tìm theo tên.',
        'Modal + Form tạo/sửa có kiểm tra (tên bắt buộc, không trùng).',
        'Bật/tắt trạng thái; hỏi xác nhận trước khi xóa hoặc ẩn danh mục còn sản phẩm.',
      ]}
      api={[
        'GET /api/admin/categories?page=&size=&q=',
        'POST /api/admin/categories',
        'PUT /api/admin/categories/{id}',
        'DELETE /api/admin/categories/{id}',
      ]}
    />
  );
}

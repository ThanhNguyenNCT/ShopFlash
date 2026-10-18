import ComingSoon from '@/shared/components/ComingSoon';

// F2 Quản lý sản phẩm (TV1). PLACEHOLDER: thay toàn bộ file này bằng màn hình thật, giữ export default.
// Route và menu đã khai báo trong ../routes.jsx.
export default function AdminProductsPage() {
  return (
    <ComingSoon
      feature="F2"
      title="Quản lý sản phẩm"
      owner="TV1"
      module="storage"
      file="frontend/src/modules/storage/pages/AdminProductsPage.jsx"
      description="Tạo, xem, chỉnh sửa sản phẩm và cập nhật tồn kho phục vụ bán hàng."
      todo={[
        'Bảng sản phẩm: SKU (sf-code), tên, danh mục, giá (PriceTag size="sm"), tồn kho (PRODUCT_INVENTORY), trạng thái; phân trang, lọc theo danh mục và trạng thái.',
        'Form tạo/sửa có kiểm tra (SKU duy nhất, giá lớn hơn 0); cập nhật tồn kho.',
        'Hỏi xác nhận trước khi ngừng bán hoặc xóa.',
        'Sửa sản phẩm phải cập nhật projection tìm kiếm trên Redis (thống nhất với TV2, module indexing).',
      ]}
      api={[
        'GET /api/admin/products?page=&size=&categoryId=&status=',
        'POST /api/admin/products',
        'PUT /api/admin/products/{id}',
        'PATCH /api/admin/products/{id}/inventory',
      ]}
    />
  );
}

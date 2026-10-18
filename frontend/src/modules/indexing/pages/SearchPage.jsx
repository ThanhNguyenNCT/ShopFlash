import { Typography } from 'antd';
import { useSearchParams } from 'react-router-dom';
import ComingSoon from '@/shared/components/ComingSoon';
import { tokens } from '@/shared/design-system';

// F3, F4 Tìm kiếm và lọc sản phẩm (TV2). PLACEHOLDER: thay toàn bộ file này bằng màn hình thật, giữ export default.
// Route và menu đã khai báo trong ../routes.jsx.
export default function SearchPage() {
  const [params] = useSearchParams();
  const value = params.get('q');
  return (
    <div style={{ display: 'grid', gap: tokens.space[4] }}>
      {value && (
        <Typography.Text type="secondary">
          Từ khóa đang tìm: <span className="sf-code">{value}</span>
        </Typography.Text>
      )}
      <ComingSoon
        feature="F3, F4"
        title="Tìm kiếm và lọc sản phẩm"
        owner="TV2"
        module="indexing"
        file="frontend/src/modules/indexing/pages/SearchPage.jsx"
        description="Kết quả tìm kiếm theo tên, SKU, danh mục; lọc nâng cao, sắp xếp và phân trang. Đây là màn hình chính để so sánh index Oracle với RediSearch."
        todo={[
          'Đọc q, danh mục, khoảng giá, sắp xếp, trang từ URL (?q=&categoryId=&minPrice=&maxPrice=&sort=&page=) để chia sẻ link và nút Back hoạt động đúng.',
          'F4: bộ lọc danh mục, khoảng giá, còn hàng; sắp xếp theo giá, mới nhất, bán chạy.',
          'Lưới ProductCard (Row/Col, 2 cột trên mobile), phân trang; bấm thẻ sang /products/:productId.',
          'Trạng thái loading (Skeleton), rỗng ("Không tìm thấy sản phẩm phù hợp"), lỗi có nút Thử lại.',
          'Ô tìm kiếm ở header đã gửi sang /search?q=..., không cần làm lại.',
          'Nên hiện nguồn dữ liệu (Oracle index hay RediSearch) và thời gian truy vấn để phục vụ báo cáo chủ đề Indexing.',
        ]}
        api={[
          'GET /api/products/search?q=&categoryId=&minPrice=&maxPrice=&sort=&page=&size=',
          'GET /api/categories (danh sách danh mục cho bộ lọc)',
        ]}
      />
    </div>
  );
}

import { Typography } from 'antd';
import { useParams } from 'react-router-dom';
import ComingSoon from '@/shared/components/ComingSoon';
import { tokens } from '@/shared/design-system';

// F3 Chi tiết sản phẩm (TV2). PLACEHOLDER: thay toàn bộ file này bằng màn hình thật, giữ export default.
// Route và menu đã khai báo trong ../routes.jsx.
export default function ProductDetailPage() {
  const value = useParams().productId;
  return (
    <div style={{ display: 'grid', gap: tokens.space[4] }}>
      {value && (
        <Typography.Text type="secondary">
          Mã sản phẩm trên URL: <span className="sf-code">{value}</span>
        </Typography.Text>
      )}
      <ComingSoon
        feature="F3"
        title="Chi tiết sản phẩm"
        owner="TV2"
        module="indexing"
        file="frontend/src/modules/indexing/pages/ProductDetailPage.jsx"
        description="Thông tin đầy đủ của một sản phẩm và thao tác thêm vào giỏ."
        todo={[
          'Ảnh, tên, SKU (class sf-code), danh mục, mô tả; giá bằng PriceTag size="lg"; tình trạng còn hàng.',
          'Nút "Thêm vào giỏ" lấy từ module concurrency (TV5 export component), không tự gọi API giỏ hàng.',
          'Nếu sản phẩm đang trong flash sale: hiện giá sale và link sang /flash-sale.',
          'Sản phẩm không tồn tại hoặc ngừng bán: Result 404 kèm nút về trang chủ. Có trạng thái loading và lỗi.',
        ]}
        api={[
          'GET /api/products/{productId}',
        ]}
      />
    </div>
  );
}

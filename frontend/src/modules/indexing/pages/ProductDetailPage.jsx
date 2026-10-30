import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Col, Flex, Result, Row, Skeleton, Tag, Typography } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { PriceTag, tokens } from '@/shared/design-system';
import AddToCartButton from '@/modules/transaction/components/AddToCartButton';
import { getProduct } from '../api';
import '../components/indexing.css';

// F3 Chi tiết sản phẩm (TV2). Luôn đọc Oracle (dữ liệu gốc, tồn kho mới nhất), không đọc bản sao Redis.
// Nút "Thêm vào giỏ" là của module transaction (TV4), chỉ gắn khi sản phẩm còn hàng.
export default function ProductDetailPage() {
  const { productId } = useParams();
  const navigate = useNavigate();
  const [state, setState] = useState({ loading: true, error: null, product: null });
  const [imageFailed, setImageFailed] = useState(false);

  const load = useCallback(() => {
    setState({ loading: true, error: null, product: null });
    setImageFailed(false);
    getProduct(productId)
      .then((product) => setState({ loading: false, error: null, product }))
      .catch((error) => setState({ loading: false, error, product: null }));
  }, [productId]);

  useEffect(() => { load(); }, [load]);

  if (state.loading) {
    return (
      <Row gutter={[tokens.space[6], tokens.space[4]]}>
        <Col xs={24} md={10}><Skeleton.Image active style={{ width: '100%', height: 320 }} /></Col>
        <Col xs={24} md={14}><Skeleton active paragraph={{ rows: 6 }} /></Col>
      </Row>
    );
  }

  const { error, product } = state;
  if (error && (error.status === 404 || error.status === 400)) {
    return (
      <Result
        status="404"
        title="Không tìm thấy sản phẩm"
        subTitle="Sản phẩm không tồn tại hoặc đã ngừng bán."
        extra={<Button type="primary" onClick={() => navigate('/')}>Về trang chủ</Button>}
      />
    );
  }
  if (error) {
    return (
      <Alert
        type="error"
        showIcon
        title="Không tải được sản phẩm"
        description={error.message}
        action={<Button onClick={load}>Thử lại</Button>}
      />
    );
  }

  return (
    <Flex vertical gap={tokens.space[4]}>
      <Link to="/search"><ArrowLeftOutlined /> Tiếp tục mua sắm</Link>
      <Row gutter={[tokens.space[6], tokens.space[4]]}>
        <Col xs={24} md={10}>
          <div className="sf-idx-thumb">
            {product.thumbnailUrl && !imageFailed
              ? <img src={product.thumbnailUrl} alt={product.name} onError={() => setImageFailed(true)} />
              : <span>Chưa có ảnh</span>}
          </div>
        </Col>
        <Col xs={24} md={14}>
          <Flex vertical gap={tokens.space[3]}>
            <Typography.Title level={2} style={{ margin: 0 }}>{product.name}</Typography.Title>
            <Flex wrap gap={tokens.space[3]} align="center">
              <Typography.Text type="secondary">SKU <span className="sf-code">{product.sku}</span></Typography.Text>
              <Link to={`/search?categoryId=${product.categoryId}`}>{product.categoryName}</Link>
            </Flex>
            <PriceTag price={product.price} size="lg" />
            <div>
              {product.inStock
                ? <Tag color="success">Còn hàng · {product.availableQuantity.toLocaleString('vi-VN')} sản phẩm</Tag>
                : <Tag>Hết hàng</Tag>}
            </div>
            {product.inStock && <div style={{ maxWidth: 320 }}><AddToCartButton productId={product.productId} /></div>}
            <div>
              <Typography.Title level={5}>Mô tả</Typography.Title>
              <Typography.Paragraph style={{ whiteSpace: 'pre-line' }}>
                {product.description || 'Chưa có mô tả.'}
              </Typography.Paragraph>
            </div>
            <Typography.Text type="secondary" style={{ fontSize: tokens.text.caption[0] }}>
              Giá và tồn kho đọc trực tiếp từ Oracle.
            </Typography.Text>
          </Flex>
        </Col>
      </Row>
    </Flex>
  );
}

import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Empty, Flex, Input, InputNumber, Modal, Table, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { formatVND, tokens } from '@/shared/design-system';
import { listEligibleProducts, replaceSaleItems } from '../api';
import { formatNumber } from '../format';

const MAX_ITEMS = 50;
const SEARCH_PAGE_SIZE = 8;

// Gợi ý khi thêm: giảm 30%, làm tròn xuống nghìn đồng; tối đa 10 suất và không vượt hàng có thể bán.
const suggestPrice = (basePrice) => Math.max(0, Math.floor((Number(basePrice) * 0.7) / 1000) * 1000);

function rowProblems(row) {
  const problems = {};
  if (row.salePrice === null || row.salePrice === undefined) problems.salePrice = 'Nhập giá sale.';
  else if (row.salePrice >= Number(row.basePrice)) problems.salePrice = `Phải thấp hơn giá gốc ${formatVND(row.basePrice)}.`;
  if (!row.allocatedQuantity) problems.allocatedQuantity = 'Nhập số suất.';
  else if (row.allocatedQuantity > row.available) problems.allocatedQuantity = `Chỉ còn ${row.available} có thể bán.`;
  return problems;
}

/** Chọn sản phẩm, giá sale và số suất cho chương trình DRAFT. Lưu là thay toàn bộ danh sách (kèm version). */
export default function SaleItemsModal({ sale, onCancel, onSaved }) {
  const [rows, setRows] = useState(() => sale.items.map((item) => ({
    productId: item.productId,
    sku: item.sku,
    name: item.productName,
    basePrice: item.basePrice,
    available: item.available,
    salePrice: Number(item.salePrice),
    allocatedQuantity: item.allocated,
  })));
  const [query, setQuery] = useState({ q: '', page: 1 });
  const [eligible, setEligible] = useState({ rows: [], page: 1, size: SEARCH_PAGE_SIZE, total: 0 });
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [showProblems, setShowProblems] = useState(false);

  const search = useCallback(async () => {
    setSearching(true);
    setSearchError(null);
    try {
      setEligible(await listEligibleProducts({ saleId: sale.flashSaleId, q: query.q, page: query.page, size: SEARCH_PAGE_SIZE }));
    } catch (problem) {
      setSearchError(problem.message ?? 'Không tải được danh sách sản phẩm.');
    } finally {
      setSearching(false);
    }
  }, [sale.flashSaleId, query]);

  useEffect(() => { search(); }, [search]);

  const chosen = new Set(rows.map((r) => r.productId));
  const update = (productId, patch) => setRows((current) => current.map((r) => (r.productId === productId ? { ...r, ...patch } : r)));
  const add = (product) => setRows((current) => [...current, {
    productId: product.productId,
    sku: product.sku,
    name: product.name,
    basePrice: product.basePrice,
    available: product.available,
    salePrice: suggestPrice(product.basePrice),
    allocatedQuantity: Math.min(10, product.available),
  }]);
  const remove = (productId) => setRows((current) => current.filter((r) => r.productId !== productId));

  const invalid = rows.some((r) => Object.keys(rowProblems(r)).length > 0);

  const save = async () => {
    setShowProblems(true);
    if (invalid || rows.length > MAX_ITEMS) return;
    setSaving(true);
    setError(null);
    try {
      const detail = await replaceSaleItems(sale.flashSaleId, {
        version: sale.version,
        items: rows.map((r) => ({ productId: r.productId, salePrice: r.salePrice, allocatedQuantity: r.allocatedQuantity })),
      });
      onSaved(detail, `Đã lưu ${rows.length} sản phẩm của chương trình.`);
    } catch (problem) {
      setError(problem.code === 'FLASH_SALE_CHANGED'
        ? 'Chương trình vừa được người khác sửa. Đóng hộp thoại để tải lại rồi chọn lại.'
        : problem.message ?? 'Không lưu được danh sách sản phẩm.');
    } finally {
      setSaving(false);
    }
  };

  const chosenColumns = [
    {
      title: 'Sản phẩm', key: 'product',
      render: (_, r) => <div className="sf-cc-name"><strong>{r.name}</strong><span className="sf-cc-code">{r.sku}</span></div>,
    },
    { title: 'Giá gốc', dataIndex: 'basePrice', align: 'right', render: (v) => <span className="sf-num">{formatVND(v)}</span> },
    {
      title: 'Giá sale (VND)', key: 'salePrice', width: tokens.space[8] * 5,
      render: (_, r) => {
        const problem = showProblems && rowProblems(r).salePrice;
        return (
          <div>
            <InputNumber aria-label={`Giá sale của ${r.name}`} min={0} precision={0} step={1000} value={r.salePrice}
              status={problem ? 'error' : undefined} onChange={(v) => update(r.productId, { salePrice: v })} />
            {problem && <Typography.Text type="danger" className="sf-cc-hint">{problem}</Typography.Text>}
          </div>
        );
      },
    },
    {
      title: 'Số suất', key: 'allocatedQuantity', width: tokens.space[8] * 4,
      render: (_, r) => {
        const problem = showProblems && rowProblems(r).allocatedQuantity;
        return (
          <div>
            <InputNumber aria-label={`Số suất của ${r.name}`} min={1} max={100000} precision={0} value={r.allocatedQuantity}
              status={problem ? 'error' : undefined} onChange={(v) => update(r.productId, { allocatedQuantity: v })} />
            <Typography.Text type={problem ? 'danger' : 'secondary'} className="sf-cc-hint">
              {problem || `Có thể bán: ${formatNumber(r.available)}`}
            </Typography.Text>
          </div>
        );
      },
    },
    {
      title: '', key: 'remove', align: 'right',
      render: (_, r) => <Button type="text" danger icon={<DeleteOutlined />} aria-label={`Bỏ ${r.name}`} onClick={() => remove(r.productId)} />,
    },
  ];

  const eligibleColumns = [
    {
      title: 'Sản phẩm', key: 'product',
      render: (_, p) => <div className="sf-cc-name"><strong>{p.name}</strong><span className="sf-cc-code">{p.sku}</span></div>,
    },
    { title: 'Giá gốc', dataIndex: 'basePrice', align: 'right', render: (v) => <span className="sf-num">{formatVND(v)}</span> },
    { title: 'Có thể bán', dataIndex: 'available', align: 'right', render: (v) => <span className="sf-num">{formatNumber(v)}</span> },
    {
      title: '', key: 'add', align: 'right',
      render: (_, p) => (chosen.has(p.productId)
        ? <Typography.Text type="secondary">Đã thêm</Typography.Text>
        : <Button icon={<PlusOutlined />} disabled={rows.length >= MAX_ITEMS} onClick={() => add(p)}>Thêm</Button>),
    },
  ];

  return (
    <Modal open title={`Sản phẩm của chương trình #${sale.flashSaleId}`} okText="Lưu danh sách" cancelText="Hủy"
      confirmLoading={saving} onOk={save} onCancel={onCancel} width="min(1040px, calc(100vw - 32px))" destroyOnHidden>
      <Flex vertical gap={tokens.space[4]} className="sf-cc-items-editor">
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          Giá sale phải thấp hơn giá gốc; số suất không vượt hàng có thể bán (tồn kho trừ phần đã giữ cho flash sale khác).
          Hàng chỉ được giữ khi lên lịch. Tối đa {MAX_ITEMS} sản phẩm.
        </Typography.Paragraph>
        <Table size="small" rowKey="productId" columns={chosenColumns} dataSource={rows} pagination={false}
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có sản phẩm. Tìm và thêm ở dưới." /> }} />
        {showProblems && invalid && <Alert type="error" showIcon title="Có dòng chưa hợp lệ, sửa các ô báo đỏ rồi lưu lại." />}
        {error && <Alert type="error" showIcon title={error} />}

        <Typography.Title level={5} style={{ margin: 0 }}>Thêm sản phẩm</Typography.Title>
        <Input.Search allowClear placeholder="Tìm theo tên hoặc SKU" enterButton="Tìm" loading={searching}
          onSearch={(q) => setQuery({ q, page: 1 })} />
        {searchError && <Alert type="error" showIcon title={searchError} action={<Button size="small" onClick={search}>Thử lại</Button>} />}
        <Table size="small" rowKey="productId" columns={eligibleColumns} dataSource={eligible.rows} loading={searching}
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: 'Không có sản phẩm phù hợp (đang bán, còn hàng, chưa thuộc chương trình khác trùng giờ).' }}
          pagination={{
            current: eligible.page, pageSize: eligible.size, total: eligible.total, showSizeChanger: false, size: 'small',
            onChange: (page) => setQuery((current) => ({ ...current, page })),
          }} />
      </Flex>
    </Modal>
  );
}

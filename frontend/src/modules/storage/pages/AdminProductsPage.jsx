import { useCallback, useEffect, useState } from 'react';
import { Alert, Avatar, Button, Card, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import { EditOutlined, InboxOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { PriceTag, tokens } from '@/shared/design-system';
import { createProduct, getCategories, getProducts, updateProduct, updateProductInventory } from '../api';
import '../storage.css';

const PRODUCT_STATUSES = [{ value: 'ACTIVE', label: 'Đang bán' }, { value: 'INACTIVE', label: 'Ngừng bán' }];

const slugify = (value = '') => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  .replace(/đ/g, 'd').replace(/Đ/g, 'd').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');

function ProductModal({ value, categories, onCancel, onSaved }) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const editing = Boolean(value);

  useEffect(() => { form.setFieldsValue(value ? { ...value, categoryId: value.categoryId } : { status: 'ACTIVE', initialOnHandQuantity: 0 }); }, [form, value]);
  const submit = async () => {
    const body = await form.validateFields();
    setSaving(true);
    try {
      const result = editing ? await updateProduct(value.productId, body) : await createProduct(body);
      onSaved(result, editing ? 'Đã cập nhật sản phẩm.' : 'Đã tạo sản phẩm và tồn kho ban đầu.');
    } catch (error) {
      form.setFields([{ name: 'name', errors: [error.message] }]);
    } finally { setSaving(false); }
  };
  return (
    <Modal open title={editing ? 'Sửa sản phẩm' : 'Tạo sản phẩm'} okText={editing ? 'Lưu thay đổi' : 'Tạo sản phẩm'} cancelText="Hủy"
      confirmLoading={saving} onCancel={onCancel} onOk={submit} width="min(680px, calc(100vw - 32px))" destroyOnHidden>
      <Form form={form} layout="vertical" requiredMark="optional">
        <Form.Item name="categoryId" label="Danh mục" rules={[{ required: true, message: 'Chọn danh mục' }]}><Select showSearch optionFilterProp="label" options={categories.map((item) => ({ value: item.categoryId, label: item.name }))} /></Form.Item>
        <Form.Item name="sku" label="SKU" rules={[{ required: true, whitespace: true, message: 'Nhập SKU' }, { max: 80 }]}><Input /></Form.Item>
        <Form.Item name="name" label="Tên sản phẩm" rules={[{ required: true, whitespace: true, message: 'Nhập tên sản phẩm' }, { max: 255 }]}><Input onChange={(event) => { if (!form.getFieldValue('slug')) form.setFieldValue('slug', slugify(event.target.value)); }} /></Form.Item>
        <Form.Item name="slug" label="Slug" extra="Để trống để tự tạo từ tên" rules={[{ max: 300 }]}><Input /></Form.Item>
        <Form.Item name="basePrice" label="Giá bán (VND)" rules={[{ required: true, message: 'Nhập giá bán' }]}><InputNumber min={0} precision={0} className="sf-storage-field-full" /></Form.Item>
        {!editing && <Form.Item name="initialOnHandQuantity" label="Tồn kho ban đầu" rules={[{ required: true, message: 'Nhập tồn kho' }]}><InputNumber min={0} precision={0} className="sf-storage-field-full" /></Form.Item>}
        <Form.Item name="thumbnailUrl" label="URL ảnh" rules={[{ max: 500 }]}><Input /></Form.Item>
        <Form.Item name="description" label="Mô tả" rules={[{ max: 10000 }]}><Input.TextArea autoSize={{ minRows: 2, maxRows: 5 }} /></Form.Item>
        <Form.Item name="status" label="Trạng thái" rules={[{ required: true }]}><Select options={PRODUCT_STATUSES} /></Form.Item>
      </Form>
    </Modal>
  );
}

function InventoryModal({ value, onCancel, onSaved }) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  useEffect(() => { form.setFieldsValue({ onHandQuantity: value?.inventory?.onHandQuantity, version: value?.inventory?.version }); }, [form, value]);
  const submit = async () => {
    const body = await form.validateFields();
    setSaving(true);
    try { onSaved(await updateProductInventory(value.productId, body), 'Đã cập nhật tồn kho.'); } catch (error) { form.setFields([{ name: 'onHandQuantity', errors: [error.message] }]); } finally { setSaving(false); }
  };
  return <Modal open title={`Cập nhật tồn kho · ${value.name}`} okText="Lưu tồn kho" cancelText="Hủy" confirmLoading={saving} onCancel={onCancel} onOk={submit} destroyOnHidden>
    <Typography.Paragraph type="secondary">Đã giữ cho Flash Sale: <span className="sf-storage-num">{value.inventory.reservedFlashQuantity}</span>. Tồn kho mới không được thấp hơn số này.</Typography.Paragraph>
    <Form form={form} layout="vertical"><Form.Item name="onHandQuantity" label="Tồn kho thực tế" rules={[{ required: true, message: 'Nhập tồn kho' }]}><InputNumber min={0} precision={0} className="sf-storage-field-full" /></Form.Item><Form.Item name="version" hidden><Input /></Form.Item></Form>
  </Modal>;
}

export default function AdminProductsPage() {
  const [filters, setFilters] = useState({ q: '', categoryId: undefined, status: undefined, page: 1, size: 20 });
  const [data, setData] = useState({ rows: [], page: 1, size: 20, total: 0 });
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [editing, setEditing] = useState(null);
  const [inventory, setInventory] = useState(null);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { setData(await getProducts(filters)); } catch (nextError) { setError(nextError); } finally { setLoading(false); }
  }, [filters]);
  const loadCategories = useCallback(async () => {
    try { setCategories((await getCategories({ page: 1, size: 100 })).rows); } catch (nextError) { setError(nextError); }
  }, []);
  useEffect(() => { load(); }, [load]);
  useEffect(() => { loadCategories(); }, [loadCategories]);

  const saved = (_, message) => { setEditing(null); setInventory(null); setCreating(false); setNotice(message); load(); loadCategories(); };
  const columns = [
    { title: '', dataIndex: 'thumbnailUrl', key: 'thumbnail', width: tokens.space[8] + tokens.space[6], render: (url) => <Avatar className="sf-storage-thumb" shape="square" src={url} icon={<InboxOutlined />} /> },
    { title: 'Sản phẩm', key: 'product', render: (_, row) => <Space direction="vertical" size={0}><Typography.Text strong>{row.name}</Typography.Text><span className="sf-storage-code">{row.sku}</span></Space> },
    { title: 'Danh mục', dataIndex: 'categoryName', key: 'category', responsive: ['md'] },
    { title: 'Giá', dataIndex: 'basePrice', key: 'price', align: 'right', render: (value) => <PriceTag price={value} size="sm" /> },
    { title: 'Tồn kho', key: 'inventory', align: 'right', render: (_, row) => <div className="sf-storage-stock"><span className="sf-storage-num">{row.inventory.normalAvailableQuantity}</span><span className="sf-storage-stock__hint">{row.inventory.onHandQuantity} thực tế · {row.inventory.reservedFlashQuantity} đã giữ</span></div> },
    { title: 'Trạng thái', dataIndex: 'status', key: 'status', render: (value) => <Tag className={`sf-storage-status sf-storage-status--${value === 'ACTIVE' ? 'active' : 'inactive'}`}>{value === 'ACTIVE' ? 'Đang bán' : 'Ngừng bán'}</Tag> },
    { title: 'Thao tác', key: 'actions', align: 'right', render: (_, row) => <Space size={tokens.space[1]}><Button type="text" icon={<EditOutlined />} onClick={() => setEditing(row)}>Sửa</Button><Button type="text" icon={<InboxOutlined />} onClick={() => setInventory(row)}>Tồn kho</Button></Space> },
  ];

  return <div className="sf-storage-page">
    <div className="sf-storage-page__head"><div className="sf-storage-page__heading"><Typography.Title level={3} style={{ margin: 0 }}>Sản phẩm</Typography.Title><Typography.Text type="secondary">Quản lý thông tin bán hàng, trạng thái và tồn kho thực tế.</Typography.Text></div><Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>Tạo sản phẩm</Button></div>
    {notice && <Alert type="success" showIcon closable message={notice} onClose={() => setNotice(null)} />}
    {error && <Alert type="error" showIcon closable message={error.message || 'Không tải được sản phẩm'} onClose={() => setError(null)} action={<Button size="small" icon={<ReloadOutlined />} onClick={load}>Thử lại</Button>} />}
    <Card><div className="sf-storage-toolbar"><Input.Search allowClear className="sf-storage-toolbar__search" placeholder="Tìm tên hoặc SKU" value={filters.q} onChange={(event) => setFilters((value) => ({ ...value, q: event.target.value, page: 1 }))} onSearch={load} /><Select allowClear className="sf-storage-toolbar__select" placeholder="Danh mục" options={categories.map((item) => ({ value: item.categoryId, label: item.name }))} value={filters.categoryId} onChange={(categoryId) => setFilters((value) => ({ ...value, categoryId, page: 1 }))} /><Select allowClear className="sf-storage-toolbar__select" placeholder="Trạng thái" options={PRODUCT_STATUSES} value={filters.status} onChange={(status) => setFilters((value) => ({ ...value, status, page: 1 }))} /><span className="sf-storage-toolbar__grow" /><Button icon={<ReloadOutlined />} onClick={load}>Tải lại</Button></div>
      <Table rowKey="productId" columns={columns} dataSource={data.rows} loading={loading} scroll={{ x: 'max-content' }} pagination={{ current: data.page, pageSize: data.size, total: data.total, showSizeChanger: true, showTotal: (total) => `${total} sản phẩm`, onChange: (page, size) => setFilters((value) => ({ ...value, page, size })) }} />
    </Card>
    {(editing || creating) && <ProductModal value={editing} categories={categories} onCancel={() => { setEditing(null); setCreating(false); }} onSaved={saved} />}
    {inventory && <InventoryModal value={inventory} onCancel={() => setInventory(null)} onSaved={saved} />}
  </div>;
}

import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { tokens } from '@/shared/design-system';
import { createCategory, deleteCategory, getCategories, updateCategory } from '../api';
import '../storage.css';

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Đang dùng' },
  { value: 'INACTIVE', label: 'Đang ẩn' },
];

const slugify = (value = '') => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  .replace(/đ/g, 'd').replace(/Đ/g, 'd').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');

function CategoryModal({ value, onCancel, onSaved }) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const editing = Boolean(value);

  useEffect(() => { form.setFieldsValue(value ?? { status: 'ACTIVE' }); }, [form, value]);

  const submit = async () => {
    const body = await form.validateFields();
    setSaving(true);
    try {
      const result = editing ? await updateCategory(value.categoryId, body) : await createCategory(body);
      onSaved(result, editing ? 'Đã cập nhật danh mục.' : 'Đã tạo danh mục.');
    } catch (error) {
      form.setFields([{ name: 'name', errors: [error.message] }]);
    } finally { setSaving(false); }
  };

  return (
    <Modal open title={editing ? 'Sửa danh mục' : 'Tạo danh mục'} okText={editing ? 'Lưu thay đổi' : 'Tạo danh mục'}
      cancelText="Hủy" confirmLoading={saving} onCancel={onCancel} onOk={submit} destroyOnHidden>
      <Form form={form} layout="vertical" requiredMark="optional">
        <Form.Item name="name" label="Tên danh mục" rules={[{ required: true, whitespace: true, message: 'Nhập tên danh mục' }, { max: 150 }]}>
          <Input autoFocus onChange={(event) => { if (!form.getFieldValue('slug')) form.setFieldValue('slug', slugify(event.target.value)); }} />
        </Form.Item>
        <Form.Item name="slug" label="Slug" extra="Để trống để tự tạo từ tên" rules={[{ max: 180 }]}><Input /></Form.Item>
        <Form.Item name="description" label="Mô tả" rules={[{ max: 1000 }]}><Input.TextArea autoSize={{ minRows: 2, maxRows: 5 }} /></Form.Item>
        <Form.Item name="status" label="Trạng thái" rules={[{ required: true }]}><Select options={STATUS_OPTIONS} /></Form.Item>
      </Form>
    </Modal>
  );
}

export default function AdminCategoriesPage() {
  const [filters, setFilters] = useState({ q: '', status: undefined, page: 1, size: 20 });
  const [data, setData] = useState({ rows: [], page: 1, size: 20, total: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [editing, setEditing] = useState(null);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true); setError(null);
    try { setData(await getCategories(filters)); } catch (nextError) { setError(nextError); } finally { setLoading(false); }
  }, [filters]);

  useEffect(() => { load(); }, [load]);

  const saved = (_, message) => { setEditing(null); setCreating(false); setNotice(message); load(); };
  const remove = async (row) => {
    try { await deleteCategory(row.categoryId); setNotice('Đã xóa danh mục.'); load(); } catch (nextError) { setError(nextError); }
  };

  const columns = [
    { title: 'Danh mục', key: 'name', render: (_, row) => <Space direction="vertical" size={0}><Typography.Text strong>{row.name}</Typography.Text><span className="sf-storage-code">/{row.slug}</span></Space> },
    { title: 'Mô tả', dataIndex: 'description', key: 'description', responsive: ['md'], render: (value) => <Typography.Text type="secondary" ellipsis>{value || '—'}</Typography.Text> },
    { title: 'Sản phẩm', dataIndex: 'productCount', key: 'productCount', align: 'right', render: (value) => <span className="sf-storage-num">{value}</span> },
    { title: 'Trạng thái', dataIndex: 'status', key: 'status', render: (value) => <Tag className={`sf-storage-status sf-storage-status--${value === 'ACTIVE' ? 'active' : 'inactive'}`}>{value === 'ACTIVE' ? 'Đang dùng' : 'Đang ẩn'}</Tag> },
    { title: 'Thao tác', key: 'actions', align: 'right', render: (_, row) => <Space size={tokens.space[1]}>
      <Button type="text" aria-label={`Sửa ${row.name}`} icon={<EditOutlined />} onClick={() => setEditing(row)}>Sửa</Button>
      <Popconfirm title="Xóa danh mục?" description="Chỉ xóa được khi danh mục không còn sản phẩm." okText="Xóa" cancelText="Hủy" okButtonProps={{ danger: true }} disabled={row.productCount > 0} onConfirm={() => remove(row)}>
        <Button type="text" danger icon={<DeleteOutlined />} disabled={row.productCount > 0}>Xóa</Button>
      </Popconfirm>
    </Space> },
  ];

  return (
    <div className="sf-storage-page">
      <div className="sf-storage-page__head"><div className="sf-storage-page__heading"><Typography.Title level={3} style={{ margin: 0 }}>Danh mục</Typography.Title><Typography.Text type="secondary">Tổ chức catalog sản phẩm và quản lý trạng thái sử dụng.</Typography.Text></div><Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>Tạo danh mục</Button></div>
      {notice && <Alert type="success" showIcon closable message={notice} onClose={() => setNotice(null)} />}
      {error && <Alert type="error" showIcon closable message={error.message || 'Không tải được danh mục'} onClose={() => setError(null)} action={<Button size="small" icon={<ReloadOutlined />} onClick={load}>Thử lại</Button>} />}
      <Card>
        <div className="sf-storage-toolbar"><Input.Search allowClear className="sf-storage-toolbar__search" placeholder="Tìm theo tên danh mục" value={filters.q} onChange={(event) => setFilters((value) => ({ ...value, q: event.target.value, page: 1 }))} onSearch={load} /><Select allowClear className="sf-storage-toolbar__select" placeholder="Trạng thái" options={STATUS_OPTIONS} value={filters.status} onChange={(status) => setFilters((value) => ({ ...value, status, page: 1 }))} /><span className="sf-storage-toolbar__grow" /><Button icon={<ReloadOutlined />} onClick={load}>Tải lại</Button></div>
        <Table rowKey="categoryId" columns={columns} dataSource={data.rows} loading={loading} scroll={{ x: 'max-content' }} pagination={{ current: data.page, pageSize: data.size, total: data.total, showSizeChanger: true, showTotal: (total) => `${total} danh mục`, onChange: (page, size) => setFilters((value) => ({ ...value, page, size })) }} />
      </Card>
      {(editing || creating) && <CategoryModal value={editing} onCancel={() => { setEditing(null); setCreating(false); }} onSaved={saved} />}
    </div>
  );
}

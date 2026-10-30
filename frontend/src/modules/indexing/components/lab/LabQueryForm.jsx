import { Button, Checkbox, Flex, Input, InputNumber, Select, Typography } from 'antd';
import { tokens } from '@/shared/design-system';
import { SORTS } from '../../api';

/**
 * Điều kiện tìm kiếm dùng chung cho mọi thí nghiệm của trang Lab. value: { q, categoryId, minPrice, maxPrice,
 * inStock, sort }. Các nút mẫu dựng nhanh ba kiểu câu hỏi của E2/E3.
 */
export default function LabQueryForm({ value, onChange, categories }) {
  const set = (changes) => onChange({ ...value, ...changes });
  const firstCategory = categories[0]?.categoryId;
  const presets = [
    { label: 'Từ khóa (E2)', value: { q: 'tai nghe', categoryId: undefined, minPrice: null, maxPrice: null, inStock: false, sort: '' } },
    { label: 'Danh mục + giá (E3-A)', value: { q: '', categoryId: firstCategory, minPrice: 100000, maxPrice: 5000000, inStock: false, sort: 'price_asc' } },
    { label: 'Chỉ giá (E3-B)', value: { q: '', categoryId: undefined, minPrice: 100000, maxPrice: 5000000, inStock: false, sort: 'price_asc' } },
    { label: 'Từ khóa + lọc', value: { q: 'tai', categoryId: firstCategory, minPrice: null, maxPrice: null, inStock: true, sort: '' } },
  ];
  const sortOptions = SORTS.filter((s) => value.q?.trim() || !s.needsKeyword);

  return (
    <Flex vertical gap={tokens.space[3]}>
      <Flex wrap gap={tokens.space[2]} align="center">
        <Typography.Text type="secondary">Mẫu:</Typography.Text>
        {presets.map((p) => (
          <Button key={p.label} size="small" onClick={() => onChange(p.value)}>{p.label}</Button>
        ))}
      </Flex>
      <Flex wrap gap={tokens.space[3]} align="center">
        <Input
          aria-label="Từ khóa"
          placeholder="Từ khóa (để trống = duyệt/lọc)"
          allowClear
          style={{ width: 240 }}
          value={value.q}
          onChange={(e) => set({ q: e.target.value, sort: '' })}
        />
        <Select
          aria-label="Danh mục"
          allowClear
          placeholder="Mọi danh mục"
          style={{ minWidth: 200 }}
          value={value.categoryId}
          onChange={(v) => set({ categoryId: v })}
          options={categories.map((c) => ({ value: c.categoryId, label: c.name }))}
        />
        <InputNumber aria-label="Giá từ" placeholder="Giá từ" min={0} step={100000} value={value.minPrice}
          onChange={(v) => set({ minPrice: v })} />
        <InputNumber aria-label="Giá đến" placeholder="Giá đến" min={0} step={100000} value={value.maxPrice}
          onChange={(v) => set({ maxPrice: v })} />
        <Checkbox checked={value.inStock} onChange={(e) => set({ inStock: e.target.checked })}>Còn hàng</Checkbox>
        <Select
          aria-label="Sắp xếp"
          style={{ minWidth: 160 }}
          value={value.sort || (value.q?.trim() ? 'relevance' : 'price_asc')}
          onChange={(v) => set({ sort: v })}
          options={sortOptions.map((s) => ({ value: s.value, label: s.label }))}
        />
      </Flex>
    </Flex>
  );
}

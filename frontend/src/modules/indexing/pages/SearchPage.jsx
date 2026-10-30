import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Flex, InputNumber, Pagination, Row, Select, Switch, Typography } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { tokens } from '@/shared/design-system';
import { getCategories, MAX_RESULTS, PAGE_SIZE, searchProducts, SORTS } from '../api';
import ProductGrid from '../components/ProductGrid';
import SourceBadge from '../components/SourceBadge';

// F3, F4 Tìm kiếm và lọc sản phẩm (TV2). Mọi điều kiện nằm trên URL (?q=&categoryId=&minPrice=&maxPrice=&inStock=
// &sort=&page=) để chia sẻ link và nút Back hoạt động đúng. Có từ khóa: backend tìm bằng RediSearch; không có: Oracle.
export default function SearchPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q')?.trim() ?? '';
  const categoryId = params.get('categoryId') ?? '';
  const minPrice = params.get('minPrice') ?? '';
  const maxPrice = params.get('maxPrice') ?? '';
  const inStock = params.get('inStock') === 'true';
  const rawSort = params.get('sort') ?? '';
  const sort = !q && rawSort === 'relevance' ? '' : rawSort;
  const page = Math.max(1, Number(params.get('page')) || 1);

  const [categories, setCategories] = useState([]);
  const [result, setResult] = useState({ loading: true, error: null, data: null, meta: null });
  const [reload, setReload] = useState(0);
  const [priceDraft, setPriceDraft] = useState({ min: minPrice, max: maxPrice });

  useEffect(() => {
    getCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    setPriceDraft({ min: minPrice, max: maxPrice });
  }, [minPrice, maxPrice]);

  useEffect(() => {
    let cancelled = false;
    setResult((r) => ({ ...r, loading: true, error: null }));
    searchProducts({ q, categoryId, minPrice, maxPrice, inStock, sort, page, size: PAGE_SIZE })
      .then((res) => !cancelled && setResult({ loading: false, error: null, data: res.data, meta: res.meta }))
      .catch((error) => !cancelled && setResult({ loading: false, error, data: null, meta: null }));
    return () => { cancelled = true; };
  }, [q, categoryId, minPrice, maxPrice, inStock, sort, page, reload]);

  /** Đổi điều kiện trên URL; đổi bộ lọc thì quay về trang 1. */
  const update = (changes) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '' || value === false) next.delete(key);
      else next.set(key, String(value));
    });
    if (!('page' in changes)) next.delete('page');
    setParams(next);
  };

  const categoryName = useMemo(
    () => categories.find((c) => String(c.categoryId) === categoryId)?.name,
    [categories, categoryId],
  );
  const total = result.data?.page.total ?? 0;
  const heading = q ? `Kết quả cho “${q}”` : categoryName ?? 'Tất cả sản phẩm';
  const sortOptions = SORTS.filter((s) => q || !s.needsKeyword);

  return (
    <Flex vertical gap={tokens.space[4]}>
      <Flex vertical gap={tokens.space[1]}>
        <Typography.Title level={2} style={{ margin: 0 }}>{heading}</Typography.Title>
        <Flex wrap gap={tokens.space[3]} align="center">
          {!result.loading && !result.error && (
            <Typography.Text type="secondary" className="sf-num">{total.toLocaleString('vi-VN')} sản phẩm</Typography.Text>
          )}
          <SourceBadge meta={result.meta} />
        </Flex>
      </Flex>

      <Row gutter={[tokens.space[4], tokens.space[4]]}>
        <Col xs={24} lg={6}>
          <Card title="Bộ lọc" size="small">
            <Flex vertical gap={tokens.space[3]}>
              <Flex vertical gap={tokens.space[1]}>
                <Typography.Text strong>Danh mục</Typography.Text>
                <Select
                  aria-label="Danh mục"
                  allowClear
                  placeholder="Tất cả danh mục"
                  value={categoryId || undefined}
                  onChange={(v) => update({ categoryId: v })}
                  options={categories.map((c) => ({
                    value: String(c.categoryId), label: `${c.name} (${c.productCount})`,
                  }))}
                />
              </Flex>
              <Flex vertical gap={tokens.space[1]}>
                <Typography.Text strong>Khoảng giá (VND)</Typography.Text>
                <Flex gap={tokens.space[2]}>
                  <InputNumber
                    aria-label="Giá từ"
                    placeholder="Từ"
                    min={0}
                    step={100000}
                    style={{ width: '100%' }}
                    value={priceDraft.min === '' ? null : Number(priceDraft.min)}
                    onChange={(v) => setPriceDraft((d) => ({ ...d, min: v ?? '' }))}
                  />
                  <InputNumber
                    aria-label="Giá đến"
                    placeholder="Đến"
                    min={0}
                    step={100000}
                    style={{ width: '100%' }}
                    value={priceDraft.max === '' ? null : Number(priceDraft.max)}
                    onChange={(v) => setPriceDraft((d) => ({ ...d, max: v ?? '' }))}
                  />
                </Flex>
                <Button onClick={() => update({ minPrice: priceDraft.min, maxPrice: priceDraft.max })}>
                  Áp dụng giá
                </Button>
              </Flex>
              <Flex justify="space-between" align="center">
                <Typography.Text strong>Chỉ hàng còn bán</Typography.Text>
                <Switch aria-label="Chỉ hàng còn bán" checked={inStock} onChange={(v) => update({ inStock: v })} />
              </Flex>
              <Flex vertical gap={tokens.space[1]}>
                <Typography.Text strong>Sắp xếp</Typography.Text>
                <Select
                  aria-label="Sắp xếp"
                  value={sort || (q ? 'relevance' : 'price_asc')}
                  onChange={(v) => update({ sort: v })}
                  options={sortOptions.map((s) => ({ value: s.value, label: s.label }))}
                />
              </Flex>
              {(categoryId || minPrice || maxPrice || inStock || sort) && (
                <Button type="link" onClick={() => setParams(q ? { q } : {})}>Xóa bộ lọc</Button>
              )}
            </Flex>
          </Card>
        </Col>
        <Col xs={24} lg={18}>
          <Flex vertical gap={tokens.space[4]}>
            <ProductGrid
              items={result.data?.items}
              loading={result.loading}
              error={result.error}
              onRetry={() => setReload((n) => n + 1)}
              cols={{ xs: 12, md: 8, xl: 6 }}
            />
            {total > PAGE_SIZE && !result.error && (
              <Flex justify="center">
                <Pagination
                  current={page}
                  pageSize={PAGE_SIZE}
                  total={Math.min(total, MAX_RESULTS)}
                  showSizeChanger={false}
                  onChange={(p) => { update({ page: p }); window.scrollTo({ top: 0 }); }}
                />
              </Flex>
            )}
          </Flex>
        </Col>
      </Row>
    </Flex>
  );
}

import { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Flex, InputNumber, Pagination, Row, Segmented, Tag, Typography } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { formatVND, tokens } from '@/shared/design-system';
import { getCategories, MAX_RESULTS, PAGE_SIZE, searchProducts, SORTS } from '../api';
import ProductGrid from '../components/ProductGrid';
import '../components/indexing.css';

// Mốc giá chọn nhanh; '' = không giới hạn phía đó.
const PRICE_PRESETS = [
  { label: 'Dưới 100.000đ', min: '', max: '100000' },
  { label: '100.000đ – 500.000đ', min: '100000', max: '500000' },
  { label: '500.000đ – 2 triệu', min: '500000', max: '2000000' },
  { label: '2 – 10 triệu', min: '2000000', max: '10000000' },
  { label: 'Trên 10 triệu', min: '10000000', max: '' },
];

const formatThousands = (v) => (v === undefined || v === null || v === '' ? '' : String(v).replace(/\B(?=(\d{3})+(?!\d))/g, '.'));
const parseThousands = (v) => (v ? v.replace(/\D/g, '') : '');

function priceLabel(min, max) {
  if (min && max) return `${formatVND(Number(min))} – ${formatVND(Number(max))}`;
  if (min) return `Từ ${formatVND(Number(min))}`;
  return `Đến ${formatVND(Number(max))}`;
}

// F3, F4 Tìm kiếm và lọc sản phẩm (TV2). Mọi điều kiện nằm trên URL (?q=&categoryId=&minPrice=&maxPrice=&sort=&page=)
// để chia sẻ link và nút Back hoạt động đúng. Có từ khóa: backend tìm bằng RediSearch; không có: Oracle.
// Nguồn và thời gian gọi DB in ra console (api.js > logSearchMeta), không hiện cho khách hàng.
export default function SearchPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get('q')?.trim() ?? '';
  const categoryId = params.get('categoryId') ?? '';
  const minPrice = params.get('minPrice') ?? '';
  const maxPrice = params.get('maxPrice') ?? '';
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
    searchProducts({ q, categoryId, minPrice, maxPrice, sort, page, size: PAGE_SIZE })
      .then((res) => !cancelled && setResult({ loading: false, error: null, data: res.data, meta: res.meta }))
      .catch((error) => !cancelled && setResult({ loading: false, error, data: null, meta: null }));
    return () => { cancelled = true; };
  }, [q, categoryId, minPrice, maxPrice, sort, page, reload]);

  /** Đổi điều kiện trên URL; đổi bộ lọc thì quay về trang 1. */
  const update = (changes) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '' || value === false) next.delete(key);
      else next.set(key, String(value));
    });
    next.delete('inStock'); // bộ lọc cũ, đã bỏ khỏi giao diện
    if (!('page' in changes)) next.delete('page');
    setParams(next);
  };

  const categoryName = useMemo(
    () => categories.find((c) => String(c.categoryId) === categoryId)?.name,
    [categories, categoryId],
  );
  const categoryTotal = useMemo(() => categories.reduce((sum, c) => sum + (c.productCount ?? 0), 0), [categories]);
  const total = result.data?.page.total ?? 0;
  const heading = q ? `Kết quả cho “${q}”` : categoryName ?? 'Tất cả sản phẩm';
  const sortOptions = SORTS.filter((s) => q || !s.needsKeyword);
  const hasFilter = Boolean(categoryId || minPrice || maxPrice || sort);

  const draftInvalid = priceDraft.min !== '' && priceDraft.max !== '' && Number(priceDraft.min) > Number(priceDraft.max);
  const draftUnchanged = String(priceDraft.min) === minPrice && String(priceDraft.max) === maxPrice;
  const applyPrice = () => {
    if (!draftInvalid) update({ minPrice: priceDraft.min, maxPrice: priceDraft.max });
  };

  return (
    <Flex vertical gap={tokens.space[4]}>
      <Flex vertical gap={tokens.space[1]}>
        <Typography.Title level={2} style={{ margin: 0 }}>{heading}</Typography.Title>
        {!result.loading && !result.error && (
          <Typography.Text type="secondary" className="sf-num">{total.toLocaleString('vi-VN')} sản phẩm</Typography.Text>
        )}
      </Flex>

      <Row gutter={[tokens.space[4], tokens.space[4]]} align="top">
        <Col xs={24} lg={6} className="sf-idx-filter">
          <Card
            title="Bộ lọc"
            size="small"
            extra={hasFilter && (
              <Button type="link" size="small" onClick={() => setParams(q ? { q } : {})}>Xóa tất cả</Button>
            )}
          >
            <Flex vertical gap={tokens.space[3]}>
              <section className="sf-idx-filter__section" aria-label="Danh mục">
                <h3 className="sf-idx-filter__title">Danh mục</h3>
                <ul className="sf-idx-cats">
                  <li>
                    <button type="button" aria-pressed={!categoryId} onClick={() => update({ categoryId: '' })}>
                      <span>Tất cả danh mục</span>
                      {categoryTotal > 0 && <span className="sf-idx-cats__count">{categoryTotal}</span>}
                    </button>
                  </li>
                  {categories.map((c) => {
                    const id = String(c.categoryId);
                    return (
                      <li key={id}>
                        <button type="button" aria-pressed={categoryId === id} onClick={() => update({ categoryId: id })}>
                          <span>{c.name}</span>
                          <span className="sf-idx-cats__count">{c.productCount}</span>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              </section>

              <section className="sf-idx-filter__section" aria-label="Khoảng giá">
                <h3 className="sf-idx-filter__title">Khoảng giá</h3>
                <Flex wrap gap={tokens.space[2]}>
                  {PRICE_PRESETS.map((p) => {
                    const active = minPrice === p.min && maxPrice === p.max;
                    return (
                      <Tag.CheckableTag
                        key={p.label}
                        checked={active}
                        onChange={() => update(active ? { minPrice: '', maxPrice: '' } : { minPrice: p.min, maxPrice: p.max })}
                      >
                        {p.label}
                      </Tag.CheckableTag>
                    );
                  })}
                </Flex>
                <Flex vertical gap={tokens.space[2]} style={{ marginTop: tokens.space[3] }}>
                  <Typography.Text type="secondary">Hoặc nhập khoảng giá (đ)</Typography.Text>
                  <Flex gap={tokens.space[2]} align="center">
                    <InputNumber
                      aria-label="Giá từ"
                      placeholder="Từ"
                      min={0}
                      step={100000}
                      controls={false}
                      formatter={formatThousands}
                      parser={parseThousands}
                      status={draftInvalid ? 'error' : undefined}
                      style={{ width: '100%' }}
                      value={priceDraft.min === '' ? null : Number(priceDraft.min)}
                      onChange={(v) => setPriceDraft((d) => ({ ...d, min: v ?? '' }))}
                      onPressEnter={applyPrice}
                    />
                    <Typography.Text type="secondary">–</Typography.Text>
                    <InputNumber
                      aria-label="Giá đến"
                      placeholder="Đến"
                      min={0}
                      step={100000}
                      controls={false}
                      formatter={formatThousands}
                      parser={parseThousands}
                      status={draftInvalid ? 'error' : undefined}
                      style={{ width: '100%' }}
                      value={priceDraft.max === '' ? null : Number(priceDraft.max)}
                      onChange={(v) => setPriceDraft((d) => ({ ...d, max: v ?? '' }))}
                      onPressEnter={applyPrice}
                    />
                  </Flex>
                  {draftInvalid && <Typography.Text type="danger">Giá “Từ” phải nhỏ hơn hoặc bằng giá “Đến”.</Typography.Text>}
                  <Button type="primary" ghost block disabled={draftInvalid || draftUnchanged} onClick={applyPrice}>
                    Áp dụng
                  </Button>
                </Flex>
              </section>
            </Flex>
          </Card>
        </Col>

        <Col xs={24} lg={18}>
          <Flex vertical gap={tokens.space[4]}>
            <Flex className="sf-idx-toolbar" wrap justify="space-between" align="center" gap={tokens.space[3]}>
              <Flex wrap gap={tokens.space[2]} align="center">
                {!categoryId && !minPrice && !maxPrice ? (
                  <Typography.Text type="secondary">Chưa chọn bộ lọc nào</Typography.Text>
                ) : (
                  <>
                    <Typography.Text type="secondary">Đang lọc:</Typography.Text>
                    {categoryId && (
                      <Tag closable onClose={(e) => { e.preventDefault(); update({ categoryId: '' }); }}>
                        {categoryName ?? `Danh mục #${categoryId}`}
                      </Tag>
                    )}
                    {(minPrice || maxPrice) && (
                      <Tag closable onClose={(e) => { e.preventDefault(); update({ minPrice: '', maxPrice: '' }); }}>
                        {priceLabel(minPrice, maxPrice)}
                      </Tag>
                    )}
                  </>
                )}
              </Flex>
              <Flex gap={tokens.space[2]} align="center" wrap>
                <Typography.Text type="secondary">Sắp xếp</Typography.Text>
                <Segmented
                  aria-label="Sắp xếp"
                  value={sort || (q ? 'relevance' : 'price_asc')}
                  onChange={(v) => update({ sort: v })}
                  options={sortOptions.map((s) => ({ value: s.value, label: s.label }))}
                />
              </Flex>
            </Flex>

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

import { apiClient } from '@/shared/api/client';

// Contract: backend/src/main/java/com/shopflash/modules/indexing/AGENTS.md (mục API).

export const SORTS = [
  { value: 'relevance', label: 'Liên quan nhất', needsKeyword: true },
  { value: 'price_asc', label: 'Giá tăng dần' },
  { value: 'price_desc', label: 'Giá giảm dần' },
];

export const PAGE_SIZE = 24;
/** Backend chỉ trả 10.000 kết quả đầu (giới hạn MAXSEARCHRESULTS của RediSearch, áp dụng cho cả hai nguồn). */
export const MAX_RESULTS = 10000;

// Bỏ tham số rỗng để backend tự điền mặc định.
function clean(params = {}) {
  return Object.fromEntries(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '' && v !== false),
  );
}

/** params: { q, categoryId, minPrice, maxPrice, inStock, sort, page, size } → { data: { items, page }, meta } */
export async function searchProducts(params) {
  const { data } = await apiClient.get('/products/search', { params: clean(params) });
  logSearchMeta(params, data?.meta);
  return data;
}

/**
 * Demo chủ đề Indexing: nguồn (RediSearch/Oracle), tuyến, thời gian gọi DB, câu lệnh in ra console trình duyệt
 * (F12 > Console) thay vì hiện trên giao diện khách hàng. Trang Lab của admin vẫn hiện đầy đủ trên giao diện.
 */
function logSearchMeta(params, meta) {
  if (!meta) return;
  const source = meta.source === 'redis' ? 'RediSearch' : 'Oracle';
  const route = meta.route === 'KEYWORD' ? 'tìm theo từ khóa' : 'duyệt và lọc';
  console.groupCollapsed(
    `[ShopFlash search] ${source} · ${route} · ${formatMs(meta.elapsedMs)} · ${meta.roundTrips} lượt gọi DB · ${meta.total} kết quả`,
  );
  console.log('Điều kiện:', clean(params));
  console.log('Câu lệnh:', meta.statement);
  if (meta.note) console.log('Ghi chú:', meta.note);
  console.log('meta:', meta);
  console.groupEnd();
}

export async function getProduct(productId) {
  const { data } = await apiClient.get(`/products/${encodeURIComponent(productId)}`);
  return data;
}

/** [{ categoryId, name, productCount }] */
export async function getCategories() {
  const { data } = await apiClient.get('/products/categories');
  return data;
}

// --- Phòng thí nghiệm (admin) ---

/** Thêm source (auto|oracle|redis), oracleVariant (AUTO|LIKE|FULL), redisVariant (SEARCH|SCAN). */
export async function labSearch(params) {
  const { data } = await apiClient.get('/admin/indexing/search', { params: clean(params), timeout: 0 });
  return data;
}

export async function labCompare(params) {
  const { data } = await apiClient.get('/admin/indexing/compare', { params: clean(params), timeout: 0 });
  return data;
}

export async function labExplain(params) {
  const { data } = await apiClient.get('/admin/indexing/explain', { params: clean(params), timeout: 0 });
  return data;
}

export async function labIndexInfo() {
  const { data } = await apiClient.get('/admin/indexing/index-info');
  return data;
}

export function formatMs(ms) {
  if (ms === null || ms === undefined) return '–';
  return `${ms < 10 ? ms.toFixed(2) : ms.toFixed(1)} ms`;
}

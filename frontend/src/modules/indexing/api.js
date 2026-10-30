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
  return data;
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

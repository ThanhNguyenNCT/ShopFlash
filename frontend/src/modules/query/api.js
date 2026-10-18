import { apiClient } from '@/shared/api/client';
import * as mock from './mocks';

// Contract: backend/src/main/java/com/shopflash/modules/query/AGENTS.md (mục API).
// VITE_USE_MOCK=true (npm run dev:mock) trả dữ liệu mẫu cùng dạng, không gọi backend.
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';
const BASE = '/admin/reports';

export const REPORTS = [
  { slug: 'revenue-by-month', label: 'Doanh thu theo tháng', feature: 'F5', joinHint: false, paged: false },
  { slug: 'revenue-by-category', label: 'Doanh thu theo danh mục', feature: 'F5', joinHint: true, paged: false },
  { slug: 'top-products', label: 'Top sản phẩm bán chạy', feature: 'F6', joinHint: true, paged: false },
  { slug: 'vip-customers', label: 'Khách VIP', feature: 'F6', joinHint: true, paged: true },
  { slug: 'unsold-products', label: 'Sản phẩm chưa từng bán', feature: 'F6', joinHint: false, paged: true },
];

export const SOURCES = ['oracle', 'redis'];
export const JOIN_HINTS = ['AUTO', 'NL', 'HASH', 'MERGE'];
export const REDIS_MODES = ['AGGREGATE', 'CLIENT'];

// Bỏ các tham số rỗng để backend tự điền mặc định.
function clean(params = {}) {
  return Object.fromEntries(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''));
}

/** params: { source, from, to, joinHint, redisMode, limit, page, size } → { data, meta } */
export async function getReport(report, params) {
  if (USE_MOCK) return mock.report(report, clean(params));
  const { data } = await apiClient.get(`${BASE}/${report}`, { params: clean(params) });
  return data;
}

/** Chạy cùng tham số trên cả hai nguồn → { report, oracle, redis, match, mismatches } */
export async function compareReport(report, params) {
  if (USE_MOCK) return mock.compare(report, clean(params));
  const { data } = await apiClient.get(`${BASE}/${report}/compare`, { params: clean(params) });
  return data;
}

/** → { source, variant, statement, lines[] } */
export async function getPlan(report, params) {
  if (USE_MOCK) return mock.plan(report, clean(params));
  const { data } = await apiClient.get(`${BASE}/${report}/plan`, { params: clean(params) });
  return data;
}

/** body: { from, to, limit, warmup, runs, variants: [{ source, variant }] } → { report, results[] } */
export async function runBenchmark(report, body = {}) {
  if (USE_MOCK) return mock.benchmark(report, body);
  const { data } = await apiClient.post(`${BASE}/${report}/benchmark`, body);
  return data;
}

/** Cùng request với runBenchmark nhưng nhận CSV; trả Blob để tải về. */
export async function downloadBenchmarkCsv(report, body = {}) {
  if (USE_MOCK) return new Blob([mock.benchmarkCsv(report, body)], { type: 'text/csv' });
  const { data } = await apiClient.post(`${BASE}/${report}/benchmark`, body, {
    headers: { Accept: 'text/csv' },
    responseType: 'blob',
  });
  return data;
}

/** → { oracle: { countedOrders, orderItems }, redis: { orderDocs, lineDocs, indexing, lastRebuildAt, lastRebuildMs }, inSync } */
export async function getAnalyticsStatus() {
  if (USE_MOCK) return mock.status();
  const { data } = await apiClient.get(`${BASE}/analytics/status`);
  return data;
}

/** Dựng lại read model Redis từ Oracle → { orders, lines, elapsedMs } */
export async function rebuildAnalytics() {
  if (USE_MOCK) return mock.rebuild();
  const { data } = await apiClient.post(`${BASE}/analytics/rebuild`);
  return data;
}

/** action: 'GATHER' | 'DELETE' → { action, tables[], elapsedMs } */
export async function changeOracleStats(action) {
  if (USE_MOCK) return mock.stats(action);
  const { data } = await apiClient.post(`${BASE}/oracle/stats`, { action });
  return data;
}

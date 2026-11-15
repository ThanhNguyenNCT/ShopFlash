import { apiClient } from '@/shared/api/client';

// Module concurrency (TV5), F9 flash sale và Concurrency Lab.
// Contract: backend/src/main/java/com/shopflash/modules/concurrency/AGENTS.md, mục "API".
// Lỗi đã được shared/api/client chuẩn hóa thành { status, code, message, details }.

const id = (value) => encodeURIComponent(value);

// --- Khách hàng ---

/** { serverTime, sales: [{ flashSaleId, name, phase: 'UPCOMING' | 'LIVE', startAt, endAt, maxQuantityPerUser, reservationTimeoutSeconds, items }] } */
export async function getActiveSales() {
  const { data } = await apiClient.get('/flash-sales/active');
  return data;
}

/** Chỉ đọc Redis: { serverTime, items: [{ itemId, remaining }] }. Gọi lại mỗi 2 s. */
export async function getStock(flashSaleId) {
  const { data } = await apiClient.get(`/flash-sales/${id(flashSaleId)}/stock`);
  return data;
}

/** Giữ suất. created = false khi backend trả lại reservation đang chờ của chính người này (200). */
export async function reserveItem(flashSaleId, itemId, quantity = 1) {
  const response = await apiClient.post(`/flash-sales/${id(flashSaleId)}/items/${id(itemId)}/reservations`, { quantity });
  return { reservation: response.data, created: response.status === 201 };
}

export async function getReservation(reservationId) {
  const { data } = await apiClient.get(`/flash-sales/reservations/${id(reservationId)}`);
  return data;
}

/** Hủy giữ suất (chỉ khi PENDING). Đã RELEASED thì backend vẫn trả 204. */
export async function cancelReservation(reservationId) {
  await apiClient.delete(`/flash-sales/reservations/${id(reservationId)}`);
}

/**
 * Đặt đơn flash sale. Gửi lại cùng reservationId là an toàn: đơn đã tạo thì backend trả lại đơn cũ (200).
 * Trả { order: { orderId, orderCode, status, totalAmount }, created }.
 */
export async function checkoutReservation(reservationId, body) {
  const response = await apiClient.post(`/flash-sales/reservations/${id(reservationId)}/checkout`, body);
  return { order: response.data, created: response.status === 201 };
}

// --- Admin: chương trình flash sale ---

const clean = (params) => Object.fromEntries(
  Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''),
);

/** { rows: [{ flashSaleId, name, status, startAt, endAt, itemCount, allocatedTotal, soldTotal }], page, size, total } */
export async function listSales({ status, page = 1, size = 20 } = {}) {
  const { data } = await apiClient.get('/admin/flash-sales', { params: clean({ status, page, size }) });
  return data;
}

export async function getSale(flashSaleId) {
  const { data } = await apiClient.get(`/admin/flash-sales/${id(flashSaleId)}`);
  return data;
}

/** body: { name, description, startAt, endAt, config: { maxQuantityPerUser, reservationTimeoutSeconds, allowCancel } } */
export async function createSale(body) {
  const { data } = await apiClient.post('/admin/flash-sales', body);
  return data;
}

/** Như createSale, thêm version. SCHEDULED/ACTIVE chỉ đổi được tên và mô tả. */
export async function updateSale(flashSaleId, body) {
  const { data } = await apiClient.put(`/admin/flash-sales/${id(flashSaleId)}`, body);
  return data;
}

/** Chỉ DRAFT. body: { version, items: [{ productId, salePrice, allocatedQuantity }] }, thay toàn bộ danh sách. */
export async function replaceSaleItems(flashSaleId, body) {
  const { data } = await apiClient.put(`/admin/flash-sales/${id(flashSaleId)}/items`, body);
  return data;
}

export async function deleteSale(flashSaleId) {
  await apiClient.delete(`/admin/flash-sales/${id(flashSaleId)}`);
}

/** action: 'schedule' | 'unschedule' | 'cancel' | 'stop'. Trả chi tiết chương trình sau khi đổi. */
export async function changeSaleStatus(flashSaleId, action) {
  const { data } = await apiClient.post(`/admin/flash-sales/${id(flashSaleId)}/${action}`);
  return data;
}

/** Sản phẩm thêm được vào chương trình saleId; q tìm theo tên hoặc SKU. */
export async function listEligibleProducts({ saleId, q, page = 1, size = 10 }) {
  const { data } = await apiClient.get('/admin/flash-sales/eligible-products', {
    params: clean({ saleId, q: q?.trim(), page, size }),
  });
  return data;
}

/** Đối chiếu Oracle với Redis của một chương trình. */
export async function getRuntime(flashSaleId) {
  const { data } = await apiClient.get(`/admin/flash-sales/${id(flashSaleId)}/runtime`);
  return data;
}

/** Dựng lại key runtime từ Oracle; reservation đang chờ bị bỏ. Trả { flashSaleId, items, droppedReservations }. */
export async function rebuildRuntime(flashSaleId) {
  const { data } = await apiClient.post(`/admin/flash-sales/${id(flashSaleId)}/runtime/rebuild`);
  return data;
}

// --- Admin: Concurrency Lab ---

/** Chạy đồng bộ, có thể lâu (vài chục giây tới vài phút) nên không đặt timeout. */
export async function runLab(body) {
  const { data } = await apiClient.post('/admin/concurrency-lab/runs', body, { timeout: 0 });
  return data;
}

export async function resetLab(quota) {
  const { data } = await apiClient.post('/admin/concurrency-lab/reset', { quota });
  return data;
}

/** { oracle: { allocated, sold, version } | null, redis: { allocated, remaining, version } | null, running } */
export async function getLabState() {
  const { data } = await apiClient.get('/admin/concurrency-lab/state');
  return data;
}

const KEY = 'shopflash.checkout.pending.v1';

export function readPendingCheckout() {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const value = JSON.parse(raw);
    if (!Number.isSafeInteger(value?.principalUserId) || value.principalUserId <= 0
        || typeof value.idempotencyKey !== 'string' || !value.body
        || typeof value.body !== 'object' || !Array.isArray(value.body.expectedLines)) return null;
    return value;
  } catch { return null; }
}

export function savePendingCheckout(value) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(value));
  } catch {
    throw new Error('Trình duyệt không lưu được yêu cầu đang chờ. Chưa gửi lệnh đặt hàng.');
  }
}

export function clearPendingCheckout(idempotencyKey) {
  try {
    if (readPendingCheckout()?.idempotencyKey === idempotencyKey) sessionStorage.removeItem(KEY);
  } catch { /* Lỗi lưu trữ sẽ được xử lý khi tạo yêu cầu tiếp theo. */ }
}

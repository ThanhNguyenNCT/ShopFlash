import { apiClient } from '@/shared/api/client';
import { isSameCustomer } from './identity';

const TOKEN_KEY = 'shopflash.transaction.localToken';

export function getLocalToken() {
  try { return sessionStorage.getItem(TOKEN_KEY) ?? ''; } catch { return ''; }
}

export function saveLocalToken(value) {
  const token = value.trim();
  if (!/^[0-9a-f]{64}$/.test(token)) {
    throw { status: 400, code: 'VALIDATION_ERROR', message: 'Token local phải gồm 64 ký tự hex chữ thường.' };
  }
  try { sessionStorage.setItem(TOKEN_KEY, token); } catch {
    throw { status: 0, code: 'STORAGE_ERROR', message: 'Trình duyệt không lưu được token trong phiên này.' };
  }
  return token;
}

export function clearLocalToken() {
  try { sessionStorage.removeItem(TOKEN_KEY); } catch { /* storage bị chặn */ }
}

function requireCustomer(user) {
  if (!user || user.role !== 'CUSTOMER') {
    throw { status: 401, code: 'AUTH_REQUIRED', message: 'Hãy chọn tài khoản Khách trước khi dùng giỏ hàng.' };
  }
}

async function authorizedRequest(user, config) {
  requireCustomer(user);
  const token = getLocalToken();
  if (!token) throw { status: 401, code: 'AUTH_REQUIRED', message: 'Hãy nhập token local để dùng giỏ hàng.' };
  const headers = { Authorization: `Bearer ${token}` };
  const identity = await apiClient.get('/transaction/local-identity', { headers });
  if (!isSameCustomer(user, identity.data)) {
    throw { status: 403, code: 'IDENTITY_MISMATCH', message: 'Token không khớp tài khoản Khách đang hiển thị. Hãy chọn lại tài khoản hoặc token.' };
  }
  const response = await apiClient.request({ ...config, headers: { ...headers, ...config.headers } });
  return response.data;
}

export const getCart = (user) => authorizedRequest(user, { method: 'get', url: '/cart' });
export const addCartItem = (user, version, productId, quantity) => authorizedRequest(user, {
  method: 'post', url: '/cart/items', headers: { 'X-Cart-Version': version }, data: { productId, quantity },
});
// Lấy phiên bản ngay trước lệnh cộng. Khi lệnh bị timeout/xung đột, caller chỉ tải lại giỏ;
// không tự gửi lại POST vì có thể đã cộng hàng thành công.
export async function addProductToCart(user, productId, quantity = 1) {
  const cart = await getCart(user);
  return addCartItem(user, cart.version, productId, quantity);
}
export const setCartItemQuantity = (user, version, productId, quantity) => authorizedRequest(user, {
  method: 'patch', url: `/cart/items/${productId}`, headers: { 'X-Cart-Version': version }, data: { quantity },
});
export const removeCartItem = (user, version, productId) => authorizedRequest(user, {
  method: 'delete', url: `/cart/items/${productId}`, headers: { 'X-Cart-Version': version },
});
export const clearCart = (user, version) => authorizedRequest(user, {
  method: 'delete', url: '/cart/items', headers: { 'X-Cart-Version': version },
});

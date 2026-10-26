import { apiClient } from '@/shared/api/client';

function requireCustomer(user) {
  if (!user || user.role !== 'CUSTOMER') {
    throw { status: 401, code: 'AUTH_REQUIRED', message: 'Hãy đăng nhập bằng tài khoản Khách trước khi dùng giỏ hàng.' };
  }
}

async function authorizedRequest(user, config) {
  requireCustomer(user);
  const response = await apiClient.request(config);
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

export const getCheckoutPreview = (user) => authorizedRequest(user, {
  method: 'get', url: '/checkout/preview',
});

export const placeOrder = (user, idempotencyKey, body) => authorizedRequest(user, {
  method: 'post', url: '/orders', headers: { 'Idempotency-Key': idempotencyKey }, data: body,
});

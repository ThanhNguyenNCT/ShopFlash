import { apiClient } from '@/shared/api/client';

// Các đường dẫn này khớp với khung UI hiện có. Khi chốt contract backend,
// cập nhật module AGENTS.md và điều chỉnh ở đúng file này.
const clean = (params = {}) => Object.fromEntries(
  Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
);

export async function getCategories(params) {
  const { data } = await apiClient.get('/admin/categories', { params: clean(params) });
  return data;
}

export async function createCategory(body) {
  const { data } = await apiClient.post('/admin/categories', body);
  return data;
}

export async function updateCategory(categoryId, body) {
  const { data } = await apiClient.put(`/admin/categories/${categoryId}`, body);
  return data;
}

export async function deleteCategory(categoryId) {
  const { data } = await apiClient.delete(`/admin/categories/${categoryId}`);
  return data;
}

export async function getProducts(params) {
  const { data } = await apiClient.get('/admin/products', { params: clean(params) });
  return data;
}

export async function createProduct(body) {
  const { data } = await apiClient.post('/admin/products', body);
  return data;
}

export async function updateProduct(productId, body) {
  const { data } = await apiClient.put(`/admin/products/${productId}`, body);
  return data;
}

export async function updateProductInventory(productId, body) {
  const { data } = await apiClient.patch(`/admin/products/${productId}/inventory`, body);
  return data;
}

export async function getProjectionStatus() {
  const { data } = await apiClient.get('/admin/catalog/projection/status');
  return data;
}

export async function rebuildProjection() {
  const { data } = await apiClient.post('/admin/catalog/projection/rebuild', {}, { timeout: 0 });
  return data;
}

export async function runConstraintDemo() {
  const { data } = await apiClient.post('/admin/catalog/constraint-demo');
  return data;
}

export async function getStorageFootprint() {
  const { data } = await apiClient.get('/admin/catalog/storage-footprint');
  return data;
}

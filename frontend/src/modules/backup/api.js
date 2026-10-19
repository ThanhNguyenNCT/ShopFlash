import { apiClient } from '@/shared/api/client';

export async function getBackupSystemStatus() {
  const { data } = await apiClient.get('/admin/backups/status');
  return data;
}

export async function getBackups(params) {
  const { data } = await apiClient.get('/admin/backups', { params });
  return data;
}

export async function createBackup() {
  const { data } = await apiClient.post('/admin/backups', {}, { timeout: 0 });
  return data;
}

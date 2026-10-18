import axios from 'axios';

// Client chung cho mọi module. Lỗi luôn được đổi về dạng { status, code, message, details }
// theo contract lỗi của backend: { "error": { code, message, details } }.
export const apiClient = axios.create({ baseURL: '/api', timeout: 120000 });

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const body = error.response?.data?.error;
    return Promise.reject({
      status: error.response?.status ?? 0,
      code: body?.code ?? (error.response ? 'HTTP_ERROR' : 'NETWORK_ERROR'),
      message: body?.message ?? error.message,
      details: body?.details ?? null,
    });
  },
);

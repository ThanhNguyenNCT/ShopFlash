import axios from 'axios';

// Client chung cho mọi module. Lỗi luôn được đổi về dạng { status, code, message, details }
// theo contract lỗi của backend: { "error": { code, message, details } }.
// Timeout mặc định 2 phút; request chạy lâu có chủ đích (đo hiệu năng, dựng lại dữ liệu) tự truyền `timeout: 0`.
export const apiClient = axios.create({ baseURL: '/api', timeout: 120000 });

// Với responseType 'blob' (tải file), lỗi JSON của backend cũng về dạng Blob: đọc lại thành object.
async function errorBody(data) {
  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    try {
      return JSON.parse(await data.text())?.error;
    } catch {
      return undefined;
    }
  }
  return data?.error;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const body = await errorBody(error.response?.data);
    // axios báo hết giờ bằng ECONNABORTED (hoặc ETIMEDOUT khi bật clarifyTimeoutError); "Request aborted" cũng là
    // ECONNABORTED nên phải xem thêm message.
    const timedOut = !error.response
      && (error.code === 'ETIMEDOUT' || (error.code === 'ECONNABORTED' && /timeout/i.test(error.message ?? '')));
    let code = body?.code ?? (error.response ? 'HTTP_ERROR' : 'NETWORK_ERROR');
    let message = body?.message ?? error.message;
    if (timedOut) {
      code = 'TIMEOUT';
      message = `Backend không trả lời sau ${Math.round((error.config?.timeout ?? 0) / 1000)} giây`;
    }
    return Promise.reject({
      status: error.response?.status ?? 0,
      code,
      message,
      details: body?.details ?? null,
    });
  },
);

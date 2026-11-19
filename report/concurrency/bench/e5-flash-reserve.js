// E5 của mục 3.5: 500 khách cùng giữ suất của một sản phẩm flash sale qua HTTP (luồng thật: JWT, Spring MVC,
// reserve.lua trên Redis). Chạy bằng k6 trong Docker, từ thư mục gốc repo:
//
//   docker run --rm -v "$(pwd -W 2>/dev/null || pwd)/report/concurrency:/work" -w /work grafana/k6 run \
//     -e BASE_URL=http://host.docker.internal:8080 bench/e5-flash-reserve.js
//
// Backend phải nghe trên mọi địa chỉ để container gọi được: chạy với SERVER_ADDRESS=0.0.0.0, profile dev.
// Mặc định: chương trình 9101, sản phẩm 9102 (50 suất), khách cc001..cc500, mỗi khách bấm "Mua ngay" đúng một lần.
// Kỳ vọng: đúng QUOTA lượt 201, phần còn lại 409 SOLD_OUT, không có lỗi khác. Suất giữ tự hết hạn sau 60 giây,
// job dọn trả lại, nên thí nghiệm không để lại dữ liệu.
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const SALE = __ENV.SALE_ID || '9101';
const ITEM = __ENV.ITEM_ID || '9102';
const USERS = Number(__ENV.USERS || 500);
const QUOTA = Number(__ENV.QUOTA || 50);
const PASSWORD = __ENV.PASSWORD || 'ShopFlash123!';

const reserved = new Counter('reserved');          // 201: giữ được suất
const soldOut = new Counter('sold_out');           // 409 SOLD_OUT
const otherResult = new Counter('other_result');   // mọi kết quả khác (lỗi)
const reserveMs = new Trend('reserve_ms', true);

export const options = {
  setupTimeout: '10m',
  scenarios: {
    rush: { executor: 'per-vu-iterations', vus: USERS, iterations: 1, maxDuration: '2m' },
  },
  thresholds: {
    reserved: [`count==${QUOTA}`],
    sold_out: [`count==${USERS - QUOTA}`],
    other_result: ['count==0'],
  },
};

// Đăng nhập trước cho cả 500 khách (bcrypt chậm), từng lô 25, để lúc đo chỉ còn lượt giữ suất.
export function setup() {
  const tokens = [];
  const headers = { 'Content-Type': 'application/json' };
  for (let start = 1; start <= USERS; start += 25) {
    const batch = [];
    for (let n = start; n < start + 25 && n <= USERS; n++) {
      const email = `cc${String(n).padStart(3, '0')}@shopflash.local`;
      batch.push(['POST', `${BASE}/api/auth/login`, JSON.stringify({ email, password: PASSWORD }), { headers }]);
    }
    for (const response of http.batch(batch)) {
      if (response.status !== 200) throw new Error(`Đăng nhập lỗi ${response.status}: ${response.body}`);
      tokens.push(response.json('accessToken'));
    }
  }
  return { tokens };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const response = http.post(`${BASE}/api/flash-sales/${SALE}/items/${ITEM}/reservations`,
    JSON.stringify({ quantity: 1 }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } });
  reserveMs.add(response.timings.duration);
  const code = response.status === 201 ? 'CREATED' : (response.json('error.code') || `HTTP_${response.status}`);
  if (code === 'CREATED') reserved.add(1);
  else if (code === 'SOLD_OUT') soldOut.add(1);
  else otherResult.add(1, { code });
  check(response, { 'giữ được suất hoặc hết suất': () => code === 'CREATED' || code === 'SOLD_OUT' });
}

export function handleSummary(data) {
  const m = data.metrics;
  const count = (name) => (m[name] ? m[name].values.count : 0);
  const summary = {
    users: USERS, quota: QUOTA, sale: SALE, item: ITEM,
    reserved: count('reserved'), soldOut: count('sold_out'), other: count('other_result'),
    reserveMs: m.reserve_ms ? m.reserve_ms.values : null,
  };
  const line = `E5: ${summary.reserved} giữ được suất, ${summary.soldOut} hết suất, ${summary.other} kết quả khác `
    + `(kỳ vọng ${QUOTA} / ${USERS - QUOTA} / 0)\n`;
  return { stdout: line + JSON.stringify(summary.reserveMs) + '\n', 'results/e5-summary.json': JSON.stringify(summary, null, 2) };
}

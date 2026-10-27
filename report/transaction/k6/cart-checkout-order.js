import http from 'k6/http';
import { Trend, Counter } from 'k6/metrics';

const apiDuration = new Trend('tx_api_duration_ms', true);
const flowDuration = new Trend('tx_flow_duration_ms', true);
const apiCount = new Counter('tx_api_count');
const flowCount = new Counter('tx_flow_count');

const fixtures = JSON.parse(open(__ENV.TX_FIXTURE_MANIFEST || './backend/target/transaction-http-fixtures.json'));
const baseUrl = (__ENV.TX_BASE_URL || 'http://127.0.0.1:5173').replace(/\/$/, '');
const concurrency = Number(__ENV.TX_CONCURRENCY || 1);
const iterations = Number(__ENV.TX_ITERATIONS || 200);
const cartSize = Number(__ENV.TX_CART_SIZE || 1);
const round = Number(__ENV.TX_ROUND || 1);
const runId = __ENV.TX_RUN_ID || 'http-local';

if (![1, 5].includes(cartSize) || ![1, 10].includes(concurrency)
    || fixtures.length < concurrency || !Number.isInteger(iterations) || iterations < 1) {
  throw new Error('Cấu hình k6: cart size 1/5, concurrency 1/10 và đủ fixture riêng cho mỗi VU.');
}
if (!__ENV.TX_BENCH_PASSWORD) throw new Error('Cần TX_BENCH_PASSWORD cho tài khoản fixture.');

export const options = {
  scenarios: {
    transaction_flow: {
      executor: 'shared-iterations',
      vus: concurrency,
      iterations,
      maxDuration: '20m',
    },
  },
  thresholds: {},
};

function parse(response) {
  try { return response.json(); } catch { return {}; }
}

function classify(response) {
  if (response.status >= 200 && response.status < 300) return 'success';
  if ([400, 404, 409].includes(response.status)) return 'rejected';
  return 'error';
}

function request(api, method, path, token, body = null, extraHeaders = {}) {
  const fixtureId = fixtures[__VU - 1].id;
  const params = {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', ...extraHeaders },
    tags: { phase: 'business', api, fixture: fixtureId, run_id: runId, cart_size: String(cartSize), concurrency: String(concurrency), round: String(round) },
    timeout: '30s',
  };
  const response = http.request(method, `${baseUrl}/api${path}`, body === null ? null : JSON.stringify(body), params);
  const outcome = classify(response);
  const payload = parse(response);
  const errorCode = outcome === 'success' ? '' : (payload.error?.code || `HTTP_${response.status}`);
  const tags = { api, outcome, error_code: errorCode, fixture: fixtureId, run_id: runId,
    cart_size: String(cartSize), concurrency: String(concurrency), round: String(round) };
  apiDuration.add(response.timings.duration, tags);
  apiCount.add(1, tags);
  return { response, payload, outcome, errorCode };
}

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (digit) => {
    const value = Math.floor(Math.random() * 16);
    return (digit === 'x' ? value : ((value & 3) | 8)).toString(16);
  });
}

export function setup() {
  const tokens = [];
  for (const fixture of fixtures.slice(0, concurrency)) {
    const response = http.post(`${baseUrl}/api/auth/login`, JSON.stringify({
      email: fixture.email, password: __ENV.TX_BENCH_PASSWORD,
    }), { headers: { 'Content-Type': 'application/json' }, tags: { phase: 'setup', api: 'auth.login' } });
    const data = parse(response);
    if (response.status !== 200 || !data.accessToken) {
      throw new Error(`Không đăng nhập được fixture ${fixture.id}: HTTP ${response.status}`);
    }
    tokens.push(data.accessToken);
  }
  return { tokens };
}

export default function (session) {
  const index = __VU - 1;
  const fixture = fixtures[index];
  const token = session.tokens[index];
  const start = Date.now();
  let outcome = 'success';
  let errorCode = '';
  let completedRequests = 0;
  const call = (...args) => {
    const result = request(...args);
    completedRequests++;
    if (result.outcome !== 'success') { outcome = result.outcome; errorCode = result.errorCode; }
    return result;
  };

  try {
    let cart = call('cart.read', 'GET', '/cart', token);
    if (outcome !== 'success') return;
    if (cart.payload.items.length > 0) {
      cart = call('cart.clear', 'DELETE', '/cart/items', token, null,
        { 'X-Cart-Version': String(cart.payload.version) });
      if (outcome !== 'success') return;
    }
    for (const productId of fixture.productIds.slice(0, cartSize)) {
      cart = call('cart.add', 'POST', '/cart/items', token, { productId, quantity: 1 },
        { 'X-Cart-Version': String(cart.payload.version) });
      if (outcome !== 'success') return;
    }
    const preview = call('checkout.preview', 'GET', '/checkout/preview', token);
    if (outcome !== 'success') return;
    const body = {
      cartVersion: preview.payload.cartVersion,
      recipientName: 'Benchmark Customer',
      recipientPhone: '0900000000',
      shippingAddress: 'Transaction fixture',
      expectedLines: preview.payload.lines.map((line) => ({
        productId: line.productId, quantity: line.quantity, unitPrice: String(line.unitPrice),
      })),
    };
    const order = call('checkout.place', 'POST', '/orders', token, body, { 'Idempotency-Key': uuid() });
    if (outcome !== 'success') return;
    const id = order.payload.orderId;
    call('orders.list', 'GET', '/orders?page=1&size=20', token);
    if (outcome !== 'success') return;
    call('orders.detail', 'GET', `/orders/${id}`, token);
    if (outcome !== 'success') return;
    call('orders.cancel', 'POST', `/orders/${id}/cancel`, token);
  } catch (error) {
    outcome = 'error';
    errorCode = 'CLIENT_EXCEPTION';
  } finally {
    const tags = { outcome, error_code: errorCode, fixture: fixture.id, run_id: runId,
      cart_size: String(cartSize), concurrency: String(concurrency), round: String(round),
      requests: String(completedRequests) };
    flowDuration.add(Date.now() - start, tags);
    flowCount.add(1, tags);
  }
}

// Dữ liệu mẫu đúng dạng contract, để làm giao diện khi backend chưa chạy. Số liệu là giả.
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const MONTHS = ['2025-11', '2025-12', '2026-01', '2026-02', '2026-03', '2026-04', '2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10'];
const CATEGORIES = ['Điện thoại', 'Laptop', 'Tai nghe', 'Đồng hồ', 'Máy ảnh', 'Phụ kiện', 'Gia dụng', 'Thời trang'];

const ROWS = {
  'revenue-by-month': MONTHS.map((month, i) => ({
    month,
    orderCount: 7800 + ((i * 937) % 1900),
    revenue: 8_400_000_000 + ((i * 1_234_567_000) % 3_900_000_000),
  })),
  'revenue-by-category': CATEGORIES.map((categoryName, i) => ({
    categoryId: 10000 + i,
    categoryName,
    quantity: 26000 - i * 2700,
    revenue: 21_000_000_000 - i * 2_350_000_000,
  })),
  'top-products': Array.from({ length: 10 }, (_, i) => ({
    rank: i + 1,
    productId: 10450 + i * 37,
    productName: `Sản phẩm mẫu ${i + 1}`,
    categoryName: CATEGORIES[i % CATEGORIES.length],
    quantity: 980 - i * 61,
    revenue: 312_000_000 - i * 19_500_000,
  })),
  'vip-customers': Array.from({ length: 20 }, (_, i) => ({
    userId: 10021 + i * 13,
    fullName: `Khách hàng ${i + 1}`,
    email: `khach${i + 1}@example.com`,
    orderCount: 24 - Math.floor(i / 2),
    totalSpent: 96_000_000 - i * 2_100_000,
  })),
  'unsold-products': Array.from({ length: 20 }, (_, i) => ({
    productId: 19800 + i,
    productName: `Sản phẩm tồn ${i + 1}`,
    categoryName: CATEGORIES[(i + 3) % CATEGORIES.length],
    basePrice: 150_000 + i * 45_000,
  })),
};

const SQL = {
  'revenue-by-month': "SELECT TO_CHAR(o.created_at AT TIME ZONE '+07:00', 'YYYY-MM') AS month, COUNT(*) AS order_count, SUM(o.total_amount) AS revenue\nFROM orders o\nWHERE o.status IN ('CONFIRMED', 'COMPLETED') AND o.created_at >= :fromTs AND o.created_at < :toTs\nGROUP BY TO_CHAR(o.created_at AT TIME ZONE '+07:00', 'YYYY-MM')\nORDER BY month",
  'revenue-by-category': 'SELECT /*+ {hint} */ c.category_id, c.name, SUM(oi.quantity), SUM(oi.line_total)\nFROM orders o JOIN order_items oi ON oi.order_id = o.order_id\nJOIN products p ON p.product_id = oi.product_id JOIN categories c ON c.category_id = p.category_id\nWHERE ... GROUP BY c.category_id, c.name ORDER BY 4 DESC',
  'top-products': 'SELECT /*+ {hint} */ p.product_id, p.name, c.name, SUM(oi.quantity), SUM(oi.line_total)\nFROM orders o JOIN order_items oi ON ... JOIN products p ON ... JOIN categories c ON ...\nWHERE ... GROUP BY ... ORDER BY 4 DESC FETCH FIRST :limit ROWS ONLY',
  'vip-customers': 'SELECT u.user_id, u.full_name, u.email, COUNT(*), SUM(o.total_amount)\nFROM orders o JOIN users u ON u.user_id = o.user_id WHERE ...\nGROUP BY u.user_id, u.full_name, u.email\nHAVING SUM(o.total_amount) > (SELECT AVG(s) FROM (SELECT SUM(total_amount) s FROM orders WHERE ... GROUP BY user_id))\nORDER BY 5 DESC OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY',
  'unsold-products': "SELECT p.product_id, p.name, c.name, p.base_price FROM products p JOIN categories c ON ...\nWHERE p.status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.product_id = p.product_id)\nORDER BY p.product_id OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
};

const HINT_TEXT = { AUTO: '', NL: 'USE_NL(oi p)', HASH: 'USE_HASH(oi p)', MERGE: 'USE_MERGE(oi p)' };
const BASE_MS = { 'revenue-by-month': 38, 'revenue-by-category': 95, 'top-products': 88, 'vip-customers': 140, 'unsold-products': 61 };
const VARIANT_FACTOR = { AUTO: 1, HASH: 1, NL: 2.4, MERGE: 1.6, AGGREGATE: 0.35, CLIENT: 6.5 };

function variantOf(source, params) {
  return source === 'redis' ? params.redisMode ?? 'AGGREGATE' : params.joinHint ?? 'AUTO';
}

function build(report, params) {
  const source = params.source ?? 'oracle';
  const variant = variantOf(source, params);
  const meta = {
    source,
    report,
    variant,
    supported: true,
    elapsedMs: Math.round(BASE_MS[report] * VARIANT_FACTOR[variant] * (0.9 + Math.random() * 0.2) * 100) / 100,
    roundTrips: source === 'redis' ? (variant === 'CLIENT' ? 37 : report === 'vip-customers' ? 2 : 1) : 1,
    statement: source === 'redis'
      ? `FT.AGGREGATE idx:order-line-analytics "@status:{CONFIRMED|COMPLETED}" GROUPBY 1 @categoryId REDUCE SUM 1 @lineTotal AS revenue`
      : SQL[report].replace('{hint}', HINT_TEXT[variant]),
    rowCount: 0,
    generatedAt: new Date().toISOString(),
    note: null,
  };
  if (source === 'redis' && report === 'unsold-products') {
    return { data: null, meta: { ...meta, supported: false, roundTrips: 0, statement: null, note: 'Redis không có NOT EXISTS / anti-join: phải duy trì sẵn tập sản phẩm đã bán.' } };
  }
  let rows = ROWS[report];
  if (report === 'top-products') rows = rows.slice(0, Number(params.limit ?? 10));
  const page = Number(params.page ?? 1);
  const size = Number(params.size ?? 20);
  const data = { rows, page: null, summary: null };
  if (report === 'vip-customers' || report === 'unsold-products') {
    data.page = { page, size, total: report === 'vip-customers' ? 1534 : 812 };
  }
  if (report === 'vip-customers') data.summary = { averageSpent: 3_120_000, customerCount: 18234 };
  return { data, meta: { ...meta, rowCount: rows.length } };
}

export async function report(slug, params) {
  await delay(300);
  return build(slug, params);
}

export async function compare(slug, params) {
  await delay(500);
  const oracle = build(slug, { ...params, source: 'oracle' });
  const redis = build(slug, { ...params, source: 'redis' });
  return { report: slug, oracle, redis, match: redis.meta.supported ? true : null, mismatches: [] };
}

export async function plan(slug, params) {
  await delay(300);
  const source = params.source ?? 'oracle';
  const variant = variantOf(source, params);
  const join = { AUTO: 'HASH JOIN', HASH: 'HASH JOIN', NL: 'NESTED LOOPS', MERGE: 'MERGE JOIN' }[variant];
  const lines = source === 'oracle'
    ? [
        'Plan hash value: 2310385127',
        '',
        '-----------------------------------------------------------------------------',
        '| Id  | Operation               | Name          | Rows  | Cost (%CPU)| Time     |',
        '-----------------------------------------------------------------------------',
        '|   0 | SELECT STATEMENT        |               |    10 |  2154   (3)| 00:00:01 |',
        '|   1 |  SORT ORDER BY          |               |    10 |  2154   (3)| 00:00:01 |',
        '|   2 |   HASH GROUP BY         |               |   812 |  2153   (3)| 00:00:01 |',
        `|   3 |    ${join.padEnd(20)} |               |  245K |  2120   (2)| 00:00:01 |`,
        '|   4 |     TABLE ACCESS FULL   | ORDERS        |   98K |   402   (1)| 00:00:01 |',
        '|   5 |     TABLE ACCESS FULL   | ORDER_ITEMS   |  245K |   711   (1)| 00:00:01 |',
        '-----------------------------------------------------------------------------',
      ]
    : variant === 'CLIENT'
      ? ['FT.SEARCH idx:order-line-analytics "@status:{CONFIRMED|COMPLETED}" LIMIT 0 10000  (lặp theo trang)', 'Gom nhóm và cộng dồn trong Java', 'Round-trip: 37']
      : ['Total profile time: 41.2', 'Iterators profile: TAG (status) Counter 245102', 'Result processors profile:', '  Index      Counter 245102', '  Loader     Counter 245102', '  Grouper    Counter 8', '  Sorter     Counter 8'];
  return { source, variant, statement: build(slug, params).meta.statement, lines };
}

const STAT_VARIANTS = [
  ['oracle', 'AUTO'], ['oracle', 'NL'], ['oracle', 'HASH'], ['oracle', 'MERGE'], ['redis', 'AGGREGATE'], ['redis', 'CLIENT'],
];

function samplesFor(slug, variant, runs) {
  return Array.from({ length: runs }, () => Math.round(BASE_MS[slug] * VARIANT_FACTOR[variant] * (0.85 + Math.random() * 0.3) * 100) / 100);
}

function summarize(source, variant, samples) {
  const sorted = [...samples].sort((a, b) => a - b);
  const mean = samples.reduce((s, x) => s + x, 0) / samples.length;
  const sd = Math.sqrt(samples.reduce((s, x) => s + (x - mean) ** 2, 0) / Math.max(1, samples.length - 1));
  const pct = (p) => sorted[Math.min(sorted.length - 1, Math.ceil(p * sorted.length) - 1)];
  return {
    source, variant, runs: samples.length,
    meanMs: Math.round(mean * 100) / 100, stdDevMs: Math.round(sd * 100) / 100,
    p50Ms: pct(0.5), p95Ms: pct(0.95),
    roundTrips: source === 'redis' && variant === 'CLIENT' ? 37 : 1,
    samplesMs: samples,
  };
}

export async function benchmark(slug, body) {
  await delay(900);
  const runs = body.runs ?? 5;
  const variants = body.variants?.length ? body.variants.map((v) => [v.source, v.variant]) : STAT_VARIANTS;
  return {
    report: slug,
    results: variants
      .filter(([source]) => !(source === 'redis' && slug === 'unsold-products'))
      .map(([source, variant]) => summarize(source, variant, samplesFor(slug, variant, runs))),
  };
}

export function benchmarkCsv(slug, body) {
  const header = 'report,source,variant,run,elapsedMs,roundTrips';
  const runs = body.runs ?? 5;
  const lines = STAT_VARIANTS.flatMap(([source, variant]) =>
    samplesFor(slug, variant, runs).map((ms, i) => `${slug},${source},${variant},${i + 1},${ms},${variant === 'CLIENT' ? 37 : 1}`));
  return [header, ...lines].join('\n');
}

let lastRebuildAt = new Date(Date.now() - 3600_000).toISOString();
let inSync = false;

export async function status() {
  await delay(200);
  return {
    oracle: { countedOrders: 98231, orderItems: 245102 },
    redis: { orderDocs: inSync ? 98231 : 98230, lineDocs: inSync ? 245102 : 245099, indexing: false, lastRebuildAt, lastRebuildMs: 41230.5 },
    inSync,
  };
}

export async function rebuild() {
  await delay(1500);
  lastRebuildAt = new Date().toISOString();
  inSync = true;
  return { orders: 98231, lines: 245102, elapsedMs: 41230.5 };
}

export async function stats(action) {
  await delay(800);
  const tables = ['ORDERS', 'ORDER_ITEMS', 'PRODUCTS', 'CATEGORIES', 'USERS'];
  const rows = { ORDERS: 100000, ORDER_ITEMS: 250311, PRODUCTS: 10000, CATEGORIES: 50, USERS: 20002 };
  return {
    action,
    tables: tables.map((table) => ({
      table,
      numRows: action === 'GATHER' ? rows[table] : null,
      lastAnalyzed: action === 'GATHER' ? new Date().toISOString() : null,
    })),
    elapsedMs: action === 'GATHER' ? 3120.2 : 210.4,
  };
}

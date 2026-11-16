// Đọc kết quả DBMS_XPLAN.DISPLAY_CURSOR (ALLSTATS LAST) thành dữ liệu có cấu trúc,
// để giao diện hiện bảng kế hoạch gọn và rút ra các con số chính (plan hash, phép join, ước lượng so với thực tế).

const JOIN_RE = /NESTED LOOPS|HASH JOIN|MERGE JOIN/i;
const DYNAMIC_RE = /dynamic (statistics|sampling)/i;

// "195K" -> 195000, "1M" -> 1e6, "" -> null
export function parseRows(v) {
  const s = String(v ?? '').trim();
  if (!s) return null;
  const m = s.match(/^([\d.]+)\s*([KMG]?)$/i);
  if (!m) return null;
  const mult = { '': 1, K: 1e3, M: 1e6, G: 1e9 }[m[2].toUpperCase()];
  return Number(m[1]) * mult;
}

/**
 * lines: các dòng của plan Oracle.
 * → { hash, columns: [tên cột], rows: [{ id, star, op, depth, name, cells: {cột: giá trị} }], dynamic, joins }
 * Không đọc được (ví dụ FT.PROFILE của Redis) thì rows rỗng.
 */
export function parseXplan(lines = []) {
  const hash = lines.map((l) => l.match(/Plan hash value:\s*(\d+)/)?.[1]).find(Boolean) ?? null;
  const dynamic = lines.some((l) => DYNAMIC_RE.test(l));
  const headerIdx = lines.findIndex((l) => /^\|\s*Id\s*\|\s*Operation/.test(l));
  if (headerIdx < 0) return { hash, columns: [], rows: [], dynamic, joins: [] };

  const split = (l) => l.split('|').slice(1, -1);
  const columns = split(lines[headerIdx]).map((c) => c.trim());
  const rows = [];
  for (let i = headerIdx + 1; i < lines.length; i++) {
    const l = lines[i];
    if (/^-+$/.test(l.trim())) {
      if (rows.length) break;
      continue;
    }
    if (!l.startsWith('|')) break;
    const raw = split(l);
    const idCell = raw[0] ?? '';
    const opCell = raw[1] ?? '';
    const cells = {};
    columns.forEach((c, j) => { if (j > 1) cells[c] = (raw[j] ?? '').trim(); });
    rows.push({
      id: Number(idCell.replace('*', '').trim()),
      star: idCell.includes('*'),
      op: opCell.trim(),
      depth: Math.max(0, opCell.length - opCell.trimStart().length - 1),
      name: (cells.Name ?? '').trim(),
      cells,
    });
  }
  const joins = [...new Set(rows.map((r) => r.op.match(JOIN_RE)?.[0]?.toUpperCase()).filter(Boolean))];
  return { hash, columns, rows, dynamic, joins };
}

export const isJoin = (op) => JOIN_RE.test(op);

/** Dòng join trên cùng (gần gốc nhất): ước lượng của optimizer cho kết quả join cuối cùng. */
export function topJoin(plan) {
  return plan.rows.find((r) => isJoin(r.op)) ?? null;
}

/** Tỉ lệ sai lệch ước lượng: max(E/A, A/E). null nếu thiếu số. */
export function estimateError(row) {
  const e = parseRows(row?.cells?.['E-Rows']);
  const a = parseRows(row?.cells?.['A-Rows']);
  if (!e || !a) return null;
  return Math.max(e / a, a / e);
}

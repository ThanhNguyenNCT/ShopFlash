import { Skeleton } from 'antd';

/**
 * Dải kết quả của một thí nghiệm: mỗi ô là một con số lớn, viền trái theo nguồn
 * (oracle = đỏ brand, redis = xanh info, good/bad = kết luận). headline là câu chốt bên dưới.
 *
 * items: [{ key, tone: 'oracle'|'redis'|'good'|'bad'|'neutral', label, value, caption }]
 */
export default function Verdict({ items, headline, loading = false }) {
  if (loading) {
    return (
      <div className="sf-verdict" aria-busy="true">
        <div className="sf-verdict__tiles">
          {[0, 1, 2].map((i) => (
            <div key={i} className="sf-verdict__tile">
              <Skeleton active title={false} paragraph={{ rows: 2 }} />
            </div>
          ))}
        </div>
      </div>
    );
  }
  if (!items?.length) return null;
  return (
    <div className="sf-verdict">
      <div className="sf-verdict__tiles">
        {items.map((it) => (
          <div key={it.key ?? it.label} className={`sf-verdict__tile sf-verdict__tile--${it.tone ?? 'neutral'}`}>
            <div className="sf-verdict__label">{it.label}</div>
            <div className="sf-verdict__value sf-num">{it.value}</div>
            {it.caption && <div className="sf-verdict__caption">{it.caption}</div>}
          </div>
        ))}
      </div>
      {headline && <div className="sf-verdict__headline">{headline}</div>}
    </div>
  );
}

import { Collapse, Empty, Table, Tag, Typography } from 'antd';
import PlanView from './PlanView';
import { isJoin, parseXplan } from './xplan';

// Cột hiện trong bảng gọn; các cột bộ nhớ (OMem, 1Mem, Used-Mem) để ở bản đầy đủ.
const NUM_COLS = ['E-Rows', 'A-Rows', 'A-Time', 'Buffers'];
const NUM_TITLE = { 'E-Rows': 'Ước lượng', 'A-Rows': 'Thực tế', 'A-Time': 'Thời gian', Buffers: 'Buffers' };

/**
 * Kế hoạch Oracle dạng bảng: cây phép toán (thụt lề), Name, ước lượng (E-Rows) và thực tế (A-Rows).
 * Dòng join tô brand. Bản gốc DBMS_XPLAN để trong phần mở rộng.
 * Không đọc được bảng (ví dụ plan của Redis) thì hiện bản chữ.
 */
export default function PlanTable({ lines, emptyText = 'Chưa có kế hoạch' }) {
  if (!lines?.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />;
  const plan = parseXplan(lines);
  if (!plan.rows.length) return <PlanView lines={lines} />;

  const cols = NUM_COLS.filter((c) => plan.columns.includes(c));
  const columns = [
    { title: 'Id', dataIndex: 'id', width: 44, render: (v, r) => <span className="sf-num">{r.star ? `${v}*` : v}</span> },
    {
      title: 'Phép toán',
      dataIndex: 'op',
      render: (v, r) => (
        <span className="sf-plan__op" style={{ paddingLeft: `${r.depth}ch` }}>
          {v}
          {r.name && <span className="sf-plan__name"> {r.name}</span>}
        </span>
      ),
    },
    ...cols.map((c) => ({
      title: NUM_TITLE[c],
      dataIndex: ['cells', c],
      align: 'right',
      render: (v) => <span className="sf-num">{v}</span>,
    })),
  ];

  return (
    <div className="sf-plan">
      <div className="sf-plan__meta">
        <Typography.Text type="secondary">Plan hash</Typography.Text>
        <span className="sf-code">{plan.hash ?? '—'}</span>
        {plan.joins.map((j) => <Tag key={j} color="red">{j}</Tag>)}
        {plan.dynamic && <Tag color="warning">dynamic sampling (không có thống kê)</Tag>}
      </div>
      <Table
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={plan.rows}
        pagination={false}
        scroll={{ x: 'max-content' }}
        rowClassName={(r) => (isJoin(r.op) ? 'sf-plan__row--join' : '')}
      />
      <Collapse
        ghost
        size="small"
        items={[{ key: 'raw', label: 'Bản đầy đủ DBMS_XPLAN (câu SQL, predicate, bộ nhớ)', children: <PlanView lines={lines} /> }]}
      />
    </div>
  );
}


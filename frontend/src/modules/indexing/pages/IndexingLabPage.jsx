import { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Descriptions, Flex, Row, Select, Table, Tabs, Tag, Typography } from 'antd';
import { tokens } from '@/shared/design-system';
import { formatMs, getCategories, labCompare, labExplain, labIndexInfo, labSearch } from '../api';
import LabQueryForm from '../components/lab/LabQueryForm';
import RunResult from '../components/lab/RunResult';
import '../components/indexing.css';

// Phòng thí nghiệm Indexing (TV2, admin): demo chủ đề Indexing trên dữ liệu thật của ứng dụng.
// Số liệu so sánh chính thức (đo trong DB, 200.000 sản phẩm, 5 lần) ở report/indexing/ket-qua.md; thời gian ở đây
// đo từ backend (gồm driver và mạng tới Docker), chỉ để minh họa.

const VARIANTS = [
  { key: 'oracle-AUTO', label: 'Oracle · AUTO (dùng index)', params: { source: 'oracle', oracleVariant: 'AUTO' } },
  { key: 'oracle-LIKE', label: 'Oracle · LIKE (không Oracle Text)', params: { source: 'oracle', oracleVariant: 'LIKE' }, keywordOnly: true },
  { key: 'oracle-FULL', label: 'Oracle · FULL (ép quét toàn bảng)', params: { source: 'oracle', oracleVariant: 'FULL' } },
  { key: 'redis-SEARCH', label: 'Redis · FT.SEARCH (dùng index)', params: { source: 'redis', redisVariant: 'SEARCH' } },
  { key: 'redis-SCAN', label: 'Redis · SCAN (không index)', params: { source: 'redis', redisVariant: 'SCAN' } },
];

const EMPTY_QUERY = { q: 'tai', categoryId: undefined, minPrice: null, maxPrice: null, inStock: false, sort: '' };

function toParams(query) {
  return {
    q: query.q?.trim(),
    categoryId: query.categoryId,
    minPrice: query.minPrice,
    maxPrice: query.maxPrice,
    inStock: query.inStock,
    sort: query.sort,
  };
}

function ErrorAlert({ error }) {
  return error ? <Alert type="error" showIcon title={`${error.code}: ${error.message}`} /> : null;
}

function RouteTab({ query }) {
  const [state, setState] = useState({ loading: false, error: null, response: null });
  const run = async () => {
    setState({ loading: true, error: null, response: null });
    try {
      setState({ loading: false, error: null, response: await labSearch(toParams(query)) });
    } catch (error) {
      setState({ loading: false, error, response: null });
    }
  };
  return (
    <Flex vertical gap={tokens.space[3]}>
      <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
        Chạy đúng như khi khách tìm: có từ khóa thì backend dùng RediSearch (E2: nhanh nhất cho tìm từ),
        không có thì dùng Oracle với index ghép (E3: nhanh nhất cho lọc và sắp xếp).
      </Typography.Paragraph>
      <Button type="primary" onClick={run} loading={state.loading} style={{ alignSelf: 'flex-start' }}>Chạy như khách hàng</Button>
      <ErrorAlert error={state.error} />
      <RunResult response={state.response} />
    </Flex>
  );
}

function CompareTab({ query }) {
  const [state, setState] = useState({ loading: false, error: null, result: null });
  const run = async () => {
    setState({ loading: true, error: null, result: null });
    try {
      setState({ loading: false, error: null, result: await labCompare(toParams(query)) });
    } catch (error) {
      setState({ loading: false, error, result: null });
    }
  };
  const { result } = state;
  return (
    <Flex vertical gap={tokens.space[3]}>
      <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
        Cùng một yêu cầu trên cả hai nguồn. Hai bên phải cho cùng tổng số và cùng tập sản phẩm trang đầu;
        lệch nghĩa là bản sao Redis chưa đồng bộ với Oracle.
      </Typography.Paragraph>
      <Button type="primary" onClick={run} loading={state.loading} style={{ alignSelf: 'flex-start' }}>So sánh hai nguồn</Button>
      <ErrorAlert error={state.error} />
      {result && (
        <>
          {result.match
            ? <Alert type="success" showIcon title="Hai nguồn cho cùng kết quả" />
            : <Alert type="warning" showIcon title="Hai nguồn khác nhau" description={result.mismatches.join(' · ')} />}
          <Row gutter={[tokens.space[4], tokens.space[4]]}>
            <Col xs={24} lg={12}><RunResult title="Oracle (AUTO)" response={result.oracle} /></Col>
            <Col xs={24} lg={12}><RunResult title="Redis (FT.SEARCH)" response={result.redis} /></Col>
          </Row>
        </>
      )}
    </Flex>
  );
}

function VariantsTab({ query }) {
  const [rows, setRows] = useState([]);
  const [running, setRunning] = useState(false);
  const hasKeyword = Boolean(query.q?.trim());

  // Chạy lần lượt, không song song: Redis xử lý từng lệnh một, chạy cùng lúc sẽ làm các lần đo chờ nhau.
  const run = async () => {
    setRunning(true);
    const out = [];
    for (const v of VARIANTS.filter((x) => hasKeyword || !x.keywordOnly)) {
      try {
        const res = await labSearch({ ...toParams(query), ...v.params });
        out.push({ key: v.key, label: v.label, total: res.meta.total, ms: res.meta.elapsedMs, roundTrips: res.meta.roundTrips, statement: res.meta.statement });
      } catch (error) {
        out.push({ key: v.key, label: v.label, error: `${error.code}: ${error.message}` });
      }
      setRows([...out]);
    }
    setRunning(false);
  };

  const columns = [
    { title: 'Cách chạy', dataIndex: 'label' },
    { title: 'Tổng kết quả', dataIndex: 'total', align: 'right', render: (v, r) => (r.error ? <Tag color="error">lỗi</Tag> : <span className="sf-num">{v?.toLocaleString('vi-VN')}</span>) },
    { title: 'Thời gian gọi DB', dataIndex: 'ms', align: 'right', render: (v) => <span className="sf-num">{formatMs(v)}</span> },
    { title: 'Lượt gọi DB', dataIndex: 'roundTrips', align: 'right', render: (v) => <span className="sf-num">{v ?? '–'}</span> },
  ];

  return (
    <Flex vertical gap={tokens.space[3]}>
      <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
        Cùng yêu cầu, có và không có index. Tổng kết quả mọi dòng phải bằng nhau. Bấm vào một dòng để xem câu lệnh.
        Thời gian đo từ backend, chỉ để minh họa; số liệu chính thức ở báo cáo (E1–E3, 200.000 sản phẩm).
      </Typography.Paragraph>
      <Button type="primary" onClick={run} loading={running} style={{ alignSelf: 'flex-start' }}>Chạy tất cả cách</Button>
      <Table
        size="small"
        rowKey="key"
        pagination={false}
        columns={columns}
        dataSource={rows}
        scroll={{ x: true }}
        expandable={{
          expandedRowRender: (r) => <pre className="sf-idx-pre">{r.error ?? r.statement}</pre>,
        }}
      />
    </Flex>
  );
}

function ExplainTab({ query }) {
  const [variant, setVariant] = useState('oracle-AUTO');
  const [state, setState] = useState({ loading: false, error: null, result: null });
  const options = VARIANTS.filter((v) => query.q?.trim() || !v.keywordOnly);
  const run = async () => {
    setState({ loading: true, error: null, result: null });
    try {
      const params = VARIANTS.find((v) => v.key === variant).params;
      setState({ loading: false, error: null, result: await labExplain({ ...toParams(query), ...params }) });
    } catch (error) {
      setState({ loading: false, error, result: null });
    }
  };
  return (
    <Flex vertical gap={tokens.space[3]}>
      <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
        Oracle: kế hoạch thực thi thật (DBMS_XPLAN, có A-Rows và Buffers): xem INDEX RANGE SCAN, DOMAIN INDEX hay
        TABLE ACCESS FULL. Redis: cây truy vấn (FT.EXPLAIN) và thời gian từng bước (FT.PROFILE).
      </Typography.Paragraph>
      <Flex wrap gap={tokens.space[2]}>
        <Select aria-label="Cách chạy" style={{ minWidth: 280 }} value={variant} onChange={setVariant}
          options={options.map((v) => ({ value: v.key, label: v.label }))} />
        <Button type="primary" onClick={run} loading={state.loading}>Xem kế hoạch</Button>
      </Flex>
      <ErrorAlert error={state.error} />
      {state.result && (
        <>
          <pre className="sf-idx-pre">{state.result.statement}</pre>
          <pre className="sf-idx-pre" style={{ maxHeight: 520 }}>{state.result.lines.join('\n')}</pre>
        </>
      )}
    </Flex>
  );
}

function IndexInfoTab() {
  const [state, setState] = useState({ loading: false, error: null, info: null });
  const load = async () => {
    setState({ loading: true, error: null, info: null });
    try {
      setState({ loading: false, error: null, info: await labIndexInfo() });
    } catch (error) {
      setState({ loading: false, error, info: null });
    }
  };
  useEffect(() => { load(); }, []);
  const { info } = state;
  const columns = [
    { title: 'Segment', dataIndex: 'segmentName', render: (v) => <span className="sf-code">{v}</span> },
    { title: 'Loại', dataIndex: 'segmentType' },
    { title: 'Thuộc', dataIndex: 'belongsTo', render: (v) => <span className="sf-code">{v}</span> },
    { title: 'Dung lượng', dataIndex: 'bytes', align: 'right', render: (v) => <span className="sf-num">{(v / 1024).toLocaleString('vi-VN')} KB</span> },
  ];
  return (
    <Flex vertical gap={tokens.space[3]}>
      <Flex justify="space-between" align="center" wrap gap={tokens.space[2]}>
        <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
          Cái giá về dung lượng: index của bảng PRODUCTS và bảng phụ Oracle Text (đĩa) so với idx:products (RAM).
        </Typography.Paragraph>
        <Button onClick={load} loading={state.loading}>Tải lại</Button>
      </Flex>
      <ErrorAlert error={state.error} />
      {info && (
        <Row gutter={[tokens.space[4], tokens.space[4]]}>
          <Col xs={24} lg={14}>
            <Card size="small" title="Oracle (USER_SEGMENTS)">
              <Table size="small" rowKey={(r) => r.segmentName + r.segmentType} pagination={false}
                columns={columns} dataSource={info.oracle} scroll={{ x: true }} />
            </Card>
          </Col>
          <Col xs={24} lg={10}>
            <Card size="small" title="Redis (FT.INFO idx:products)">
              <Descriptions size="small" column={1}
                items={Object.entries(info.redis).map(([k, v]) => ({ key: k, label: <span className="sf-code">{k}</span>, children: <span className="sf-num">{v}</span> }))} />
            </Card>
          </Col>
        </Row>
      )}
    </Flex>
  );
}

export default function IndexingLabPage() {
  const [categories, setCategories] = useState([]);
  const [query, setQuery] = useState(EMPTY_QUERY);

  useEffect(() => {
    getCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  const items = [
    { key: 'route', label: '1. Định tuyến', children: <RouteTab query={query} /> },
    { key: 'compare', label: '2. So sánh hai nguồn', children: <CompareTab query={query} /> },
    { key: 'variants', label: '3. Có / không index', children: <VariantsTab query={query} /> },
    { key: 'explain', label: '4. Kế hoạch thực thi', children: <ExplainTab query={query} /> },
    { key: 'size', label: '5. Dung lượng index', children: <IndexInfoTab /> },
  ];

  return (
    <Flex vertical gap={tokens.space[4]}>
      <div>
        <Typography.Title level={3} style={{ margin: 0 }}>Indexing Lab</Typography.Title>
        <Typography.Text type="secondary">
          F3/F4: cùng một yêu cầu tìm kiếm trên Oracle (B-tree, Oracle Text) và Redis (RediSearch), có và không có index.
        </Typography.Text>
      </div>
      <Card size="small" title="Điều kiện tìm kiếm (dùng cho các thẻ 1–4)">
        <LabQueryForm value={query} onChange={setQuery} categories={categories} />
      </Card>
      <Tabs items={items} defaultActiveKey="route" />
    </Flex>
  );
}

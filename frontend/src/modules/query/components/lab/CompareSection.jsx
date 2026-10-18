import { useState } from 'react';
import { Alert, Button, Card, Col, Flex, Row, Space, Table, Typography } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import { formatVND, tokens } from '@/shared/design-system';
import { compareReport } from '../../api';
import { EMPTY, formatRange } from '../../format';
import ErrorAlert from './ErrorAlert';
import MatchBadge from './MatchBadge';
import MetaInfo from './MetaInfo';
import ReportPicker, { DEFAULT_PICK } from './ReportPicker';
import ResultTable from './ResultTable';
import StaleAlert from './StaleAlert';
import StatementView from './StatementView';
import { isMoneyField, rangeParams, reportLabel, reportParams, runKey, sourceLabel, useApi } from './labUtils';

const fmtValue = (field, v) => (v === null || v === undefined ? EMPTY : isMoneyField(field) ? formatVND(v) : String(v));

const MISMATCH_COLUMNS = [
  { title: 'Khóa', dataIndex: 'key', render: (v) => <span className="sf-code">{v}</span> },
  { title: 'Trường', dataIndex: 'field', render: (v) => <span className="sf-code">{v}</span> },
  { title: 'Oracle', dataIndex: 'oracle', align: 'right', render: (v, r) => <span className="sf-num">{fmtValue(r.field, v)}</span> },
  { title: 'Redis', dataIndex: 'redis', align: 'right', render: (v, r) => <span className="sf-num">{fmtValue(r.field, v)}</span> },
];

function SourcePanel({ source, result, report }) {
  const meta = result?.meta;
  return (
    <Card size="small" title={sourceLabel(source)}>
      <Space orientation="vertical" size={tokens.space[3]} className="sf-lab-full">
        <MetaInfo meta={meta} />
        {meta?.supported === false ? (
          <Alert type="warning" showIcon title={`${sourceLabel(source)} không hỗ trợ báo cáo này`} description={meta.note} />
        ) : (
          <ResultTable report={report} rows={result?.data?.rows} />
        )}
        <StatementView statement={meta?.statement} collapsible label={source === 'redis' ? 'Lệnh Redis' : 'Câu SQL'} />
      </Space>
    </Card>
  );
}

// D1: cùng báo cáo, cùng tham số chạy trên Oracle và Redis phải ra cùng kết quả.
export default function CompareSection({ range }) {
  const [pick, setPick] = useState(DEFAULT_PICK);
  const { data, loading, error, run } = useApi(compareReport);
  // Tham số của lần chạy gần nhất, để ghi lên kết quả và báo khi tham số đang chọn đã khác.
  const [ran, setRan] = useState(null);
  const params = { ...rangeParams(range), ...reportParams(pick.report, pick) };
  const key = runKey(pick.report, params);

  const execute = () => {
    setRan({ key, range });
    run(pick.report, params);
  };

  return (
    <Space orientation="vertical" size={tokens.space[4]} className="sf-lab-full">
      <Typography.Paragraph>
        Oracle là nguồn gốc; Redis giữ read model denormalized dựng lại từ Oracle. Cùng một báo cáo và cùng tham số, backend chạy trên
        cả hai rồi so từng dòng theo khóa (tiền làm tròn 2 chữ số). Khác nhau nằm ở cách chạy (SQL nhiều bảng so với FT.AGGREGATE trên một
        index), không phải ở kết quả.
      </Typography.Paragraph>
      <Flex wrap gap={tokens.space[3]} align="center">
        <ReportPicker value={pick} onChange={setPick} />
        <Button type="primary" icon={<PlayCircleOutlined />} loading={loading} onClick={execute}>
          So sánh hai nguồn
        </Button>
      </Flex>
      <ErrorAlert error={error} title="Không so sánh được" />
      {data && (
        <>
          <StaleAlert stale={ran?.key !== key} action="So sánh hai nguồn" />
          <Card size="small">
            <Flex wrap gap={tokens.space[3]} align="center">
              <MatchBadge match={data.match} />
              <Typography.Text>
                {reportLabel(data.report)} · {formatRange(ran?.range)}
                {data.match === null && ' — một bên không hỗ trợ báo cáo này nên không so được.'}
                {data.match === false && ` — ${data.mismatches?.length ?? 0} chỗ lệch (tối đa 5 được liệt kê).`}
                {data.match === true && ' — mọi dòng trùng khóa và trùng giá trị.'}
              </Typography.Text>
            </Flex>
            {data.mismatches?.length > 0 && (
              <Table
                size="small"
                columns={MISMATCH_COLUMNS}
                dataSource={data.mismatches}
                rowKey={(r) => `${r.key}-${r.field}`}
                pagination={false}
                scroll={{ x: 'max-content' }}
                style={{ marginTop: tokens.space[3] }}
              />
            )}
          </Card>
          <Row gutter={[tokens.space[4], tokens.space[4]]}>
            <Col xs={24} xl={12}>
              <SourcePanel source="oracle" result={data.oracle} report={data.report} />
            </Col>
            <Col xs={24} xl={12}>
              <SourcePanel source="redis" result={data.redis} report={data.report} />
            </Col>
          </Row>
        </>
      )}
    </Space>
  );
}

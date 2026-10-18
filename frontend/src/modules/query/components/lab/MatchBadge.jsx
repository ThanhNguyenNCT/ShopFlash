import { Tag } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, MinusCircleOutlined } from '@ant-design/icons';

// match: true = Khớp, false = Lệch, null = một bên không hỗ trợ.
export default function MatchBadge({ match }) {
  if (match === true) {
    return <Tag className="sf-lab-match" color="success" icon={<CheckCircleOutlined />}>Khớp</Tag>;
  }
  if (match === false) {
    return <Tag className="sf-lab-match" color="error" icon={<CloseCircleOutlined />}>Lệch</Tag>;
  }
  return <Tag className="sf-lab-match" icon={<MinusCircleOutlined />}>Không so được</Tag>;
}

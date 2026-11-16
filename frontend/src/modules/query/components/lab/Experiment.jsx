import { Flex, Typography } from 'antd';
import { tokens } from '@/shared/design-system';

/** Khung chung của một khối trong Query Lab: một dòng mô tả, hàng điều khiển, rồi kết quả. */
export default function Experiment({ description, controls, children }) {
  return (
    <Flex vertical gap={tokens.space[4]} className="sf-lab-full">
      {description && <Typography.Text type="secondary">{description}</Typography.Text>}
      {controls && (
        <Flex wrap gap={tokens.space[3]} align="center">
          {controls}
        </Flex>
      )}
      {children}
    </Flex>
  );
}

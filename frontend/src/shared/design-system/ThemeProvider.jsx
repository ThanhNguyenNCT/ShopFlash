import { ConfigProvider, App as AntdApp } from 'antd';
import viVN from 'antd/locale/vi_VN';
import { antdTheme, applyCssVariables } from './theme';
import './global.css';

// Gắn biến CSS ngay khi module được nạp, trước lần render đầu, để không bị nháy màu.
applyCssVariables();

// Bọc toàn bộ ứng dụng một lần trong src/app: <ThemeProvider><Router /></ThemeProvider>
export default function ThemeProvider({ children }) {
  return (
    <ConfigProvider theme={antdTheme} locale={viVN}>
      <AntdApp>{children}</AntdApp>
    </ConfigProvider>
  );
}

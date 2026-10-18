import { RouterProvider } from 'react-router-dom';
import { ThemeProvider } from '@/shared/design-system';
import { AuthProvider } from '@/shared/auth';
import { router } from './router';

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </ThemeProvider>
  );
}

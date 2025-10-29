import { createRoot } from 'react-dom/client';
import App from './pages/App.tsx';
import Shell from './pages/Shell.tsx';
import Login from './pages/Login.tsx';
import Command from './pages/Command.tsx';
import History from './pages/History.tsx';
import ManageUsers from './pages/ManageUsers.tsx';
import ProtectedRoute from './components/ProtectedRoute.tsx';
import { BrowserRouter, Routes, Route } from "react-router";
import 'bootstrap/dist/css/bootstrap.css';

createRoot(document.getElementById('root')!).render(
  <BrowserRouter>
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <App />
            </ProtectedRoute>
          }
        />
      <Route
        path="/clientshell"
        element={
          <ProtectedRoute>
            <Shell />
          </ProtectedRoute>
        }
      />
      <Route
        path="/clientcommand"
        element={
          <ProtectedRoute>
            <Command />
            </ProtectedRoute>
          }
        />
      <Route
        path="/clienthistory"
        element={
          <ProtectedRoute>
            <History />
            </ProtectedRoute>
          }
        />
      <Route
        path="/manageusers"
        element={
          <ProtectedRoute>
            <ManageUsers />
            </ProtectedRoute>
          }
        />
    </Routes>
  </BrowserRouter>
);
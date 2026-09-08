import axios from 'axios';
import { toast } from 'sonner'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';

export const apiClient = axios.create({
  baseURL: `${API_BASE}/api/v1/admin`,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('admin_token');
      window.location.href = '/login';
    } else if (error.response?.status === 403) {
      localStorage.removeItem('admin_token');
      toast.error('접근 권한이 없습니다. 관리자에게 문의하세요.');
      window.location.href = '/login';
    } else if (error.response?.status >= 500) {
      toast.error('서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
    }
    return Promise.reject(error);
  }
);

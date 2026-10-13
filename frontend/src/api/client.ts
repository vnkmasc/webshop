import axios, { AxiosError } from 'axios';
import type { ApiError, ApiResponse, TokenResponse } from './types';

/** Same-origin API: nginx (or the Vite dev proxy) routes /api to the backend. */
export const http = axios.create({ baseURL: '/api', timeout: 30_000 });

const STORAGE_KEY = 'sb_session';

function readSession(): TokenResponse | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as TokenResponse) : null;
  } catch {
    return null;
  }
}

let session: TokenResponse | null = readSession();
const listeners = new Set<(s: TokenResponse | null) => void>();

export function getSession() {
  return session;
}

export function setSession(next: TokenResponse | null) {
  session = next;
  try {
    if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // storage unavailable (private mode): keep the session in memory only
  }
  listeners.forEach((l) => l(next));
}

export function onSessionChange(listener: (s: TokenResponse | null) => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

http.interceptors.request.use((config) => {
  if (session) config.headers.Authorization = `Bearer ${session.accessToken}`;
  return config;
});

http.interceptors.response.use(
  (res) => res,
  (error: AxiosError<ApiError>) => {
    // Expired or invalid token: drop the session so the UI shows the logged-out state.
    if (error.response?.status === 401 && session && !error.config?.url?.includes('/auth/login')) {
      setSession(null);
    }
    return Promise.reject(error);
  },
);

export async function getData<T>(url: string, params?: object): Promise<T> {
  const res = await http.get<ApiResponse<T>>(url, { params, paramsSerializer: { indexes: null } });
  return res.data.data;
}

export async function postData<T>(url: string, body?: unknown): Promise<T> {
  const res = await http.post<ApiResponse<T>>(url, body);
  return res.data.data;
}

export async function putData<T>(url: string, body?: unknown): Promise<T> {
  const res = await http.put<ApiResponse<T>>(url, body);
  return res.data.data;
}

/** User-facing message for any API error. */
export function errorMessage(err: unknown, fallback = 'Đã xảy ra lỗi, vui lòng thử lại'): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as ApiError | undefined;
    if (data?.details?.length) return data.details.map((d) => d.message).join('; ');
    if (data?.message) return data.message;
    if (err.code === 'ECONNABORTED') return 'Máy chủ phản hồi quá lâu, vui lòng thử lại';
    if (!err.response) return 'Không kết nối được máy chủ';
  }
  return fallback;
}

export function errorCode(err: unknown): string | undefined {
  return axios.isAxiosError(err) ? (err.response?.data as ApiError | undefined)?.errorCode : undefined;
}

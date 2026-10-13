import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { getSession, onSessionChange, setSession } from '@/api/client';
import { shopApi } from '@/api/shop';
import type { TokenResponse, User } from '@/api/types';

interface AuthState {
  user: User | null;
  login: (email: string, password: string) => Promise<User>;
  register: (body: { email: string; password: string; fullName: string; phone?: string }) => Promise<User>;
  logout: () => void;
  updateUser: (user: User) => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setLocal] = useState<TokenResponse | null>(getSession());

  useEffect(() => onSessionChange(setLocal), []);

  const value = useMemo<AuthState>(() => ({
    user: session?.user ?? null,
    async login(email, password) {
      const s = await shopApi.login(email, password);
      setSession(s);
      return s.user;
    },
    async register(body) {
      const s = await shopApi.register(body);
      setSession(s);
      return s.user;
    },
    logout() {
      setSession(null);
    },
    updateUser(user) {
      const current = getSession();
      if (current) setSession({ ...current, user });
    },
  }), [session]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}

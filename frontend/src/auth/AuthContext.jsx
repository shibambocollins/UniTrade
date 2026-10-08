// Keeps "who is logged in" for the whole app. The JWT is stored in localStorage so a page refresh keeps you logged in.
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { apiRequest } from '../api/client.js';

const TOKEN_KEY = 'unitrade.token';

// localStorage can throw (private windows, blocked site data), so every access is wrapped: the app still works without it.
function readToken() {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}
function saveToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* ignore: the user just has to log in again after a refresh */
  }
}

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(readToken);
  const [user, setUser] = useState(null);
  // "loading" while we confirm a stored token with the server, so protected pages do not flash the login screen
  const [loading, setLoading] = useState(() => readToken() !== null);

  useEffect(() => {
    const stored = readToken();
    if (!stored) return undefined;
    let cancelled = false;
    apiRequest('/api/auth/me', { token: stored })
      .then((me) => {
        if (!cancelled) setUser(me);
      })
      .catch((e) => {
        if (cancelled) return;
        // 401 = the token expired or is no longer valid (e.g. the server restarted with a new key): forget it.
        // Any other failure (server asleep/unreachable) keeps the stored token so a reload can try again.
        if (e.status === 401) {
          saveToken(null);
          setToken(null);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const startSession = useCallback((response) => {
    saveToken(response.token);
    setToken(response.token);
    setUser(response.user);
  }, []);

  const login = useCallback(
    async (email, password) => startSession(await apiRequest('/api/auth/login', { method: 'POST', body: { email, password } })),
    [startSession],
  );

  const register = useCallback(
    async (fullName, email, password) =>
      startSession(await apiRequest('/api/auth/register', { method: 'POST', body: { fullName, email, password } })),
    [startSession],
  );

  const logout = useCallback(() => {
    saveToken(null);
    setToken(null);
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, token, loading, login, register, logout }),
    [user, token, loading, login, register, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}

// Loads data from the API when a screen opens, and exposes loading / error / retry so every screen
// handles those states the same way (NFR3). Pass path = null to skip the request.
import { useCallback, useEffect, useState } from 'react';
import { apiRequest } from '../api/client.js';

export function useApi(path, { token } = {}) {
  const [state, setState] = useState({ data: null, error: null, loading: path !== null });
  const [attempt, setAttempt] = useState(0); // bumping this re-runs the request ("Try again")

  useEffect(() => {
    if (path === null) {
      setState({ data: null, error: null, loading: false });
      return undefined;
    }
    let cancelled = false;
    setState({ data: null, error: null, loading: true });
    apiRequest(path, { token })
      .then((data) => !cancelled && setState({ data, error: null, loading: false }))
      .catch((e) => !cancelled && setState({ data: null, error: e, loading: false }));
    return () => {
      cancelled = true; // ignore the answer if the screen was closed or the path changed meanwhile
    };
  }, [path, token, attempt]);

  const reload = useCallback(() => setAttempt((n) => n + 1), []);
  return { ...state, reload };
}

import { useCallback, useEffect, useState } from 'react';
import { apiRequest } from '../api/client.js';
import { ErrorMessage, Loading } from '../components/StatusViews.jsx';

// Shows whether the API and its database are reachable. Handy for a marker or when demoing a deployment.
export default function StatusPage() {
  const [health, setHealth] = useState(null);
  const [error, setError] = useState(null);

  const checkHealth = useCallback(async () => {
    setError(null);
    setHealth(null);
    try {
      setHealth(await apiRequest('/api/health'));
    } catch (e) {
      setError(e.message);
    }
  }, []);

  useEffect(() => {
    checkHealth();
  }, [checkHealth]);

  return (
    <section>
      <h1>System status</h1>
      <p className="muted">Is the UniTrade server running?</p>

      <div className="card">
        <h2 className="card-title">Server</h2>
        {error && <ErrorMessage message={error} onRetry={checkHealth} />}
        {!error && !health && <Loading label="Checking the server…" />}
        {health && (
          <p>
            API: <strong>{health.status}</strong> · Database: <strong>{health.database}</strong>
          </p>
        )}
      </div>
    </section>
  );
}

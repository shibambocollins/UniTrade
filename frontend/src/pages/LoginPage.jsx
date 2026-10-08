import { useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthContext.jsx';
import FormField from '../components/FormField.jsx';

export default function LoginPage() {
  const { user, login } = useAuth();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  // Already logged in (or just logged in): go back to the page the visitor came from, or home.
  if (user) return <Navigate to={location.state?.from || '/'} replace />;

  async function handleSubmit(event) {
    event.preventDefault();
    const found = {};
    if (!email.trim()) found.email = 'Email is required';
    if (!password) found.password = 'Password is required';
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) return;

    setSubmitting(true);
    try {
      await login(email.trim(), password);
    } catch (e) {
      setErrors(e.fieldErrors ?? {});
      setFormError(e.fieldErrors ? null : e.message);
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-page">
      <h1>Welcome back</h1>
      <p className="muted">Sign in to your account</p>

      <form className="card form" onSubmit={handleSubmit} noValidate>
        <FormField
          id="email"
          label="University email"
          type="email"
          inputMode="email"
          autoComplete="username"
          placeholder="you@mycput.ac.za"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={errors.email}
        />
        <FormField
          id="password"
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
        />
        {formError && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}
        <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Logging in…' : 'Log in'}
        </button>
      </form>

      <p className="auth-switch">
        New here? <Link to="/register">Create an account</Link>
      </p>
    </section>
  );
}

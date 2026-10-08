import { useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthContext.jsx';
import FormField from '../components/FormField.jsx';

export default function RegisterPage() {
  const { user, register } = useAuth();
  const location = useLocation();
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  if (user) return <Navigate to={location.state?.from || '/'} replace />;

  async function handleSubmit(event) {
    event.preventDefault();
    // Quick checks so the user gets instant feedback; the server repeats them and decides about the email domain.
    const found = {};
    if (!fullName.trim()) found.fullName = 'Full name is required';
    if (!email.trim()) found.email = 'Email is required';
    if (password.length < 8) found.password = 'Password must be 8 to 72 characters';
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) return;

    setSubmitting(true);
    try {
      await register(fullName.trim(), email.trim(), password);
    } catch (e) {
      setErrors(e.fieldErrors ?? {});
      setFormError(e.fieldErrors ? null : e.message);
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-page">
      <h1>Create account</h1>
      <p className="muted">Join the campus community</p>

      <form className="card form" onSubmit={handleSubmit} noValidate>
        <FormField
          id="fullName"
          label="Full name"
          autoComplete="name"
          placeholder="Jane Dlamini"
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          error={errors.fullName}
        />
        <FormField
          id="email"
          label="University email"
          type="email"
          inputMode="email"
          autoComplete="username"
          placeholder="230064019@mycput.ac.za"
          hint="Students only: the address must end in @mycput.ac.za"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={errors.email}
        />
        <FormField
          id="password"
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="At least 8 characters"
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
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <p className="auth-switch">
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </section>
  );
}

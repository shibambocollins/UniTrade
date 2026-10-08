import { useState } from 'react';
import { useNavigate } from 'react-router';
import { apiRequest } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import FormField from '../components/FormField.jsx';
import { POST_CATEGORIES } from '../lib/bulletin.js';

export default function BulletinFormPage() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ title: '', body: '', category: 'ANNOUNCEMENT' });
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const set = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }));

  async function handleSubmit(event) {
    event.preventDefault();
    const found = {};
    if (!form.title.trim()) found.title = 'Title is required';
    if (!form.body.trim()) found.body = 'Write something in the post';
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) return;

    setSubmitting(true);
    try {
      const saved = await apiRequest('/api/bulletin', {
        method: 'POST',
        token,
        body: { title: form.title.trim(), body: form.body.trim(), category: form.category },
      });
      navigate(`/bulletin/${saved.id}`);
    } catch (e) {
      setErrors(e.fieldErrors ?? {});
      setFormError(e.fieldErrors ? null : e.message);
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-page">
      <h1>New post</h1>
      <p className="muted">Share a notice, an event or free help. Buying and selling belongs in Listings.</p>
      <form className="card form" onSubmit={handleSubmit} noValidate>
        <FormField id="title" label="Title" value={form.title} onChange={set('title')} error={errors.title} maxLength={150} />
        <FormField id="category" label="Category" as="select" value={form.category} onChange={set('category')} error={errors.category}>
          {POST_CATEGORIES.map(([value, label]) => (
            <option key={value} value={value}>{label.replace(/s$/, '')}</option>
          ))}
        </FormField>
        <FormField id="body" label="Post" as="textarea" rows={6} maxLength={2000} value={form.body} onChange={set('body')} error={errors.body} />
        {formError && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}
        <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Posting…' : 'Publish post'}
        </button>
      </form>
    </section>
  );
}

// "Sell something" (/listings/new) and "Edit listing" (/listings/:id/edit) share this form.
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { apiRequest } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import FormField from '../components/FormField.jsx';
import { ErrorMessage, Loading } from '../components/StatusViews.jsx';
import { useApi } from '../hooks/useApi.js';
import { CATEGORIES } from '../lib/listings.js';

const EMPTY = { title: '', description: '', category: '', type: 'GOOD', price: '', condition: 'USED', imageUrl: '' };

export default function ListingFormPage() {
  const { id } = useParams(); // undefined when creating
  const editing = id !== undefined;
  const { user, token } = useAuth();
  const navigate = useNavigate();

  // When editing, load the current values first
  const existing = useApi(editing ? `/api/listings/${id}` : null, { token });
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (editing && existing.data) {
      const l = existing.data;
      setForm({
        title: l.title,
        description: l.description ?? '',
        category: l.category,
        type: l.type,
        price: String(l.price),
        condition: l.condition ?? 'USED',
        imageUrl: l.imageUrl ?? '',
      });
    }
  }, [editing, existing.data]);

  const set = (field) => (event) => setForm((current) => ({ ...current, [field]: event.target.value }));

  async function handleSubmit(event) {
    event.preventDefault();
    const found = {};
    if (!form.title.trim()) found.title = 'Title is required';
    if (!form.category) found.category = 'Choose a category';
    if (form.price === '' || Number.isNaN(Number(form.price))) found.price = 'Enter a price (0 or more)';
    else if (Number(form.price) < 0) found.price = 'Price cannot be negative';
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) return;

    setSubmitting(true);
    try {
      const saved = await apiRequest(editing ? `/api/listings/${id}` : '/api/listings', {
        method: editing ? 'PUT' : 'POST',
        token,
        body: {
          title: form.title.trim(),
          description: form.description.trim(),
          category: form.category,
          type: form.type,
          price: Number(form.price),
          condition: form.type === 'GOOD' ? form.condition : null,
          imageUrl: form.imageUrl.trim(),
        },
      });
      navigate(`/listings/${saved.id}`);
    } catch (e) {
      setErrors(e.fieldErrors ?? {});
      setFormError(e.fieldErrors ? null : e.message);
      setSubmitting(false);
    }
  }

  if (editing && existing.loading) return <Loading label="Loading your listing…" />;
  if (editing && existing.error) return <ErrorMessage message={existing.error.message} onRetry={existing.reload} />;
  if (editing && existing.data.seller.id !== user.id) {
    return <ErrorMessage message="You can only edit your own listings." />;
  }

  return (
    <section className="auth-page">
      <h1>{editing ? 'Edit listing' : 'Sell something'}</h1>
      <p className="muted">{editing ? 'Update the details below.' : 'List an item or a service for fellow students.'}</p>

      <form className="card form" onSubmit={handleSubmit} noValidate>
        <FormField id="title" label="Title" value={form.title} onChange={set('title')} error={errors.title} maxLength={150} />
        <FormField id="type" label="I am selling" as="select" value={form.type} onChange={set('type')} error={errors.type}>
          <option value="GOOD">Goods (an item)</option>
          <option value="SERVICE">A service</option>
        </FormField>
        <FormField id="category" label="Category" as="select" value={form.category} onChange={set('category')} error={errors.category}>
          <option value="">Choose…</option>
          {CATEGORIES.map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </FormField>
        {form.type === 'GOOD' && (
          <FormField id="condition" label="Condition" as="select" value={form.condition} onChange={set('condition')} error={errors.condition}>
            <option value="USED">Used</option>
            <option value="NEW">New</option>
          </FormField>
        )}
        <FormField
          id="price"
          label="Price (R)"
          type="number"
          inputMode="decimal"
          min="0"
          step="0.01"
          value={form.price}
          onChange={set('price')}
          error={errors.price}
        />
        <FormField
          id="description"
          label="Description"
          as="textarea"
          rows={4}
          maxLength={2000}
          value={form.description}
          onChange={set('description')}
          error={errors.description}
        />
        <FormField
          id="imageUrl"
          label="Photo link (optional)"
          type="url"
          inputMode="url"
          placeholder="https://…"
          hint="Paste the web address of a photo"
          value={form.imageUrl}
          onChange={set('imageUrl')}
          error={errors.imageUrl}
        />
        {formError && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}
        <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Saving…' : editing ? 'Save changes' : 'Publish listing'}
        </button>
      </form>
    </section>
  );
}

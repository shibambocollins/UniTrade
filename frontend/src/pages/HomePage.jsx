// Home = the marketplace feed: search box, filters and the paged list of ACTIVE listings (FR3).
// The chosen search lives in the address bar (?q=lamp&category=ELECTRONICS&page=2), so the Back button,
// refresh and shared links all keep working.
import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useAuth } from '../auth/AuthContext.jsx';
import ListingCard from '../components/ListingCard.jsx';
import { EmptyState, ErrorMessage, Loading } from '../components/StatusViews.jsx';
import { useApi } from '../hooks/useApi.js';
import { CATEGORIES } from '../lib/listings.js';

const PAGE_SIZE = 12;
const FILTER_KEYS = ['q', 'category', 'type', 'condition', 'minPrice', 'maxPrice'];

export default function HomePage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();

  // What is currently applied (from the address bar)
  const applied = Object.fromEntries(FILTER_KEYS.map((key) => [key, params.get(key) ?? '']));
  const page = Math.max(1, Number(params.get('page')) || 1);
  const hasFilters = FILTER_KEYS.some((key) => applied[key] !== '');

  // What is typed in the form but not applied yet; re-synced when the address bar changes (Back button, Clear)
  const [draft, setDraft] = useState(applied);
  const [formError, setFormError] = useState(null);
  const paramsText = params.toString();
  useEffect(() => {
    setDraft(Object.fromEntries(FILTER_KEYS.map((key) => [key, params.get(key) ?? ''])));
    setFormError(null);
  }, [paramsText]); // eslint-disable-line react-hooks/exhaustive-deps

  const apiQuery = new URLSearchParams({ page: String(page - 1), size: String(PAGE_SIZE) });
  FILTER_KEYS.forEach((key) => applied[key] && apiQuery.set(key, applied[key]));
  const { data, error, loading, reload } = useApi(`/api/listings?${apiQuery}`);

  const set = (field) => (event) => setDraft((current) => ({ ...current, [field]: event.target.value }));

  function handleSearch(event) {
    event.preventDefault();
    if (draft.minPrice !== '' && draft.maxPrice !== '' && Number(draft.minPrice) > Number(draft.maxPrice)) {
      setFormError('The minimum price cannot be more than the maximum price.');
      return;
    }
    if ([draft.minPrice, draft.maxPrice].some((value) => value !== '' && Number(value) < 0)) {
      setFormError('Prices cannot be negative.');
      return;
    }
    setFormError(null);
    const next = new URLSearchParams();
    FILTER_KEYS.forEach((key) => draft[key].trim() && next.set(key, draft[key].trim()));
    setParams(next); // a new search always starts at page 1
  }

  function goToPage(target) {
    const next = new URLSearchParams(params);
    if (target <= 1) next.delete('page');
    else next.set('page', String(target));
    setParams(next);
  }

  return (
    <section>
      <div className="page-head">
        <div>
          <h1>Welcome to UniTrade</h1>
          <p className="muted">Buy, sell and share with fellow CPUT students.</p>
        </div>
        {!user ? (
          <div className="actions">
            <Link to="/register" className="btn btn-primary">Create account</Link>
            <Link to="/login" className="btn btn-secondary">Log in</Link>
          </div>
        ) : (
          <div className="actions">
            <Link to="/listings/new" className="btn btn-primary">Sell something</Link>
          </div>
        )}
      </div>

      <form className="card search" onSubmit={handleSearch} role="search" noValidate>
        <div className="search-row">
          <input
            type="search"
            aria-label="Search listings"
            placeholder="Search textbooks, services, furniture…"
            value={draft.q}
            onChange={set('q')}
            maxLength={100}
          />
          <button type="submit" className="btn btn-primary">Search</button>
        </div>

        <details className="filters" open={hasFilters && FILTER_KEYS.slice(1).some((key) => applied[key] !== '')}>
          <summary>Filters</summary>
          <div className="filter-grid">
            <label>
              Category
              <select value={draft.category} onChange={set('category')}>
                <option value="">All categories</option>
                {CATEGORIES.map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </label>
            <label>
              Type
              <select value={draft.type} onChange={set('type')}>
                <option value="">Goods and services</option>
                <option value="GOOD">Goods</option>
                <option value="SERVICE">Services</option>
              </select>
            </label>
            <label>
              Condition
              <select value={draft.condition} onChange={set('condition')}>
                <option value="">Any condition</option>
                <option value="NEW">New</option>
                <option value="USED">Used</option>
              </select>
            </label>
            <label>
              Min price (R)
              <input type="number" inputMode="decimal" min="0" step="1" value={draft.minPrice} onChange={set('minPrice')} />
            </label>
            <label>
              Max price (R)
              <input type="number" inputMode="decimal" min="0" step="1" value={draft.maxPrice} onChange={set('maxPrice')} />
            </label>
          </div>
        </details>

        {formError && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}
        {hasFilters && (
          <button type="button" className="btn btn-secondary" onClick={() => setParams(new URLSearchParams())}>
            Clear search and filters
          </button>
        )}
      </form>

      {loading && <Loading label="Loading listings…" />}
      {error && <ErrorMessage message={error.message} onRetry={reload} />}

      {data && data.items.length === 0 && (
        <EmptyState title={hasFilters ? 'No listings match your search' : 'Nothing is for sale yet'}>
          <p className="muted">
            {hasFilters ? 'Try a different word or remove some filters.' : 'Be the first to list something!'}
          </p>
          <Link to={user ? '/listings/new' : '/register'} className="btn btn-primary">
            {user ? 'Sell something' : 'Create an account to sell'}
          </Link>
        </EmptyState>
      )}

      {data && data.items.length > 0 && (
        <>
          <p className="muted result-count" aria-live="polite">
            {data.totalItems} {data.totalItems === 1 ? 'listing' : 'listings'}
          </p>
          <div className="listing-grid">
            {data.items.map((listing) => (
              <ListingCard key={listing.id} listing={listing} />
            ))}
          </div>
          {data.totalPages > 1 && (
            <nav className="pager" aria-label="Pages">
              <button type="button" className="btn btn-secondary" disabled={page <= 1} onClick={() => goToPage(page - 1)}>
                Previous
              </button>
              <span>
                Page {page} of {data.totalPages}
              </span>
              <button
                type="button"
                className="btn btn-secondary"
                disabled={page >= data.totalPages}
                onClick={() => goToPage(page + 1)}
              >
                Next
              </button>
            </nav>
          )}
        </>
      )}

      <p className="muted footer-link">
        <Link to="/status">System status</Link>
      </p>
    </section>
  );
}

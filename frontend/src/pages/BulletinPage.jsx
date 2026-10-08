// FR5: the community bulletin board (public to read; students post).
import { Link, useSearchParams } from 'react-router';
import { EmptyState, ErrorMessage, Loading } from '../components/StatusViews.jsx';
import { useApi } from '../hooks/useApi.js';
import { POST_CATEGORIES, formatDate, postCategoryLabel } from '../lib/bulletin.js';

const PAGE_SIZE = 10;

export default function BulletinPage() {
  const [params, setParams] = useSearchParams();
  const category = params.get('category') ?? '';
  const page = Math.max(1, Number(params.get('page')) || 1);

  const query = new URLSearchParams({ page: String(page - 1), size: String(PAGE_SIZE) });
  if (category) query.set('category', category);
  const { data, error, loading, reload } = useApi(`/api/bulletin?${query}`);

  function chooseCategory(value) {
    setParams(value ? { category: value } : {}); // a new filter starts at page 1
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
          <h1>Bulletin board</h1>
          <p className="muted">Announcements, events and free help from students. Not for selling.</p>
        </div>
        <Link to="/bulletin/new" className="btn btn-primary">New post</Link>
      </div>

      <div className="chips" role="group" aria-label="Filter by category">
        <button type="button" className={category === '' ? 'chip chip-on' : 'chip'} aria-pressed={category === ''} onClick={() => chooseCategory('')}>
          All
        </button>
        {POST_CATEGORIES.map(([value, label]) => (
          <button
            key={value}
            type="button"
            className={category === value ? 'chip chip-on' : 'chip'}
            aria-pressed={category === value}
            onClick={() => chooseCategory(value)}
          >
            {label}
          </button>
        ))}
      </div>

      {loading && <Loading label="Loading posts…" />}
      {error && <ErrorMessage message={error.message} onRetry={reload} />}
      {data && data.items.length === 0 && (
        <EmptyState title="No posts here yet">
          <p className="muted">Be the first to share something with the community.</p>
          <Link to="/bulletin/new" className="btn btn-primary">Write a post</Link>
        </EmptyState>
      )}
      {data && data.items.length > 0 && (
        <>
          <ul className="line-list">
            {data.items.map((post) => (
              <li key={post.id} className="post">
                <p className="post-meta">
                  <span className="badge badge-paid">{postCategoryLabel(post.category)}</span>{' '}
                  <span className="muted">{post.author.fullName} · {formatDate(post.createdAt)}</span>
                </p>
                <Link to={`/bulletin/${post.id}`} className="line-title">{post.title}</Link>
                <p className="post-excerpt">{post.body.length > 140 ? `${post.body.slice(0, 140)}…` : post.body}</p>
              </li>
            ))}
          </ul>
          {data.totalPages > 1 && (
            <nav className="pager" aria-label="Pages">
              <button type="button" className="btn btn-secondary" disabled={page <= 1} onClick={() => goToPage(page - 1)}>
                Previous
              </button>
              <span>
                Page {page} of {data.totalPages}
              </span>
              <button type="button" className="btn btn-secondary" disabled={page >= data.totalPages} onClick={() => goToPage(page + 1)}>
                Next
              </button>
            </nav>
          )}
        </>
      )}
    </section>
  );
}

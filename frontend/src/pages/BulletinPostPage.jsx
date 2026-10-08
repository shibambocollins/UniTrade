import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { apiRequest } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { ErrorMessage, Loading } from '../components/StatusViews.jsx';
import { useApi } from '../hooks/useApi.js';
import { formatDate, postCategoryLabel } from '../lib/bulletin.js';

export default function BulletinPostPage() {
  const { id } = useParams();
  const { user, token } = useAuth();
  const navigate = useNavigate();
  const { data: post, error, loading, reload } = useApi(`/api/bulletin/${id}`);
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);

  async function handleDelete() {
    setDeleting(true);
    setDeleteError(null);
    try {
      await apiRequest(`/api/bulletin/${id}`, { method: 'DELETE', token });
      navigate('/bulletin');
    } catch (e) {
      setDeleteError(e.message);
      setDeleting(false);
      setConfirming(false);
    }
  }

  if (loading) return <Loading label="Loading post…" />;
  if (error) {
    return (
      <>
        <ErrorMessage message={error.message} onRetry={error.status === 404 ? undefined : reload} />
        <p className="auth-switch"><Link to="/bulletin">Back to the bulletin board</Link></p>
      </>
    );
  }

  const isAuthor = user && user.id === post.author.id;

  return (
    <article>
      <p>
        <span className="badge badge-paid">{postCategoryLabel(post.category)}</span>
      </p>
      <h1>{post.title}</h1>
      <p className="muted">
        Posted by {post.author.fullName} on {formatDate(post.createdAt)}
      </p>
      <p className="detail-description">{post.body}</p>

      {deleteError && (
        <p className="form-error" role="alert">
          {deleteError}
        </p>
      )}
      <div className="actions">
        <Link to="/bulletin" className="btn btn-secondary">Back to the board</Link>
        {isAuthor &&
          (!confirming ? (
            <button type="button" className="btn btn-danger" onClick={() => setConfirming(true)}>
              Delete
            </button>
          ) : (
            <>
              <span className="confirm-text">Delete this post?</span>
              <button type="button" className="btn btn-danger" onClick={handleDelete} disabled={deleting}>
                {deleting ? 'Deleting…' : 'Yes, delete'}
              </button>
              <button type="button" className="btn btn-secondary" onClick={() => setConfirming(false)} disabled={deleting}>
                Cancel
              </button>
            </>
          ))}
      </div>
    </article>
  );
}

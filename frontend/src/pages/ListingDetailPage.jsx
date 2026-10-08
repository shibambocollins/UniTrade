import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { apiRequest } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { useCart } from '../cart/CartContext.jsx';
import { ListingImage } from '../components/ListingCard.jsx';
import { RatingSummary } from '../components/Stars.jsx';
import { ErrorMessage, Loading } from '../components/StatusViews.jsx';
import { useApi } from '../hooks/useApi.js';
import { categoryLabel, conditionLabel, formatPrice, typeLabel } from '../lib/listings.js';

export default function ListingDetailPage() {
  const { id } = useParams();
  const { user, token } = useAuth();
  const navigate = useNavigate();
  const { data: listing, error, loading, reload } = useApi(`/api/listings/${id}`);
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(null);
  const cart = useCart();
  const [cartMessage, setCartMessage] = useState(null);

  function handleAddToCart() {
    const result = cart.add(listing);
    setCartMessage(result.ok ? null : result.message);
  }

  async function handleDelete() {
    setDeleting(true);
    setDeleteError(null);
    try {
      await apiRequest(`/api/listings/${id}`, { method: 'DELETE', token });
      navigate('/my-listings');
    } catch (e) {
      setDeleteError(e.message);
      setDeleting(false);
      setConfirming(false);
    }
  }

  if (loading) return <Loading label="Loading listing…" />;
  if (error) {
    return (
      <>
        <ErrorMessage message={error.message} onRetry={error.status === 404 ? undefined : reload} />
        <p className="auth-switch"><Link to="/">Back to browsing</Link></p>
      </>
    );
  }

  const isOwner = user && user.id === listing.seller.id;
  const sold = listing.status !== 'ACTIVE';

  return (
    <article className="detail">
      <ListingImage listing={listing} className="detail-img" />
      <div className="detail-body">
        <h1>{listing.title}</h1>
        <p className="detail-price">{formatPrice(listing.price)}</p>
        {sold && <span className="badge badge-sold">{listing.status}</span>}
        <p className="listing-card-meta">
          {categoryLabel(listing.category)} · {typeLabel(listing.type)}
          {listing.condition ? ` · ${conditionLabel(listing.condition)}` : ''}
        </p>
        <p className="muted">
          Sold by <Link to={`/users/${listing.seller.id}`}>{listing.seller.fullName}</Link>
        </p>
        {listing.seller.reviewCount !== undefined && (
          <p>
            <RatingSummary average={listing.seller.averageRating} count={listing.seller.reviewCount} />
          </p>
        )}
        {listing.description && <p className="detail-description">{listing.description}</p>}

        {deleteError && (
          <p className="form-error" role="alert">
            {deleteError}
          </p>
        )}

        {!isOwner && !sold && (
          <div className="actions">
            {!user ? (
              <Link to="/login" state={{ from: `/listings/${listing.id}` }} className="btn btn-primary">
                Log in to buy
              </Link>
            ) : cart.has(listing.id) ? (
              <>
                <span className="confirm-text">In your cart</span>
                <Link to="/cart" className="btn btn-secondary">View cart</Link>
              </>
            ) : (
              <button type="button" className="btn btn-primary" onClick={handleAddToCart}>
                Add to cart
              </button>
            )}
          </div>
        )}
        {cartMessage && (
          <p className="form-error" role="alert">
            {cartMessage}
          </p>
        )}

        {isOwner && !sold && (
          <div className="actions">
            <Link to={`/listings/${listing.id}/edit`} className="btn btn-secondary">Edit</Link>
            {!confirming ? (
              <button type="button" className="btn btn-danger" onClick={() => setConfirming(true)}>
                Delete
              </button>
            ) : (
              <>
                <span className="confirm-text">Delete this listing?</span>
                <button type="button" className="btn btn-danger" onClick={handleDelete} disabled={deleting}>
                  {deleting ? 'Deleting…' : 'Yes, delete'}
                </button>
                <button type="button" className="btn btn-secondary" onClick={() => setConfirming(false)} disabled={deleting}>
                  Cancel
                </button>
              </>
            )}
          </div>
        )}
      </div>
    </article>
  );
}

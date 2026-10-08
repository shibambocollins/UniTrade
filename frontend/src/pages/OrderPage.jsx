import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { apiRequest } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import FormField from '../components/FormField.jsx';
import { StarInput, Stars } from '../components/Stars.jsx';
import { ErrorMessage, Loading } from '../components/StatusViews.jsx';
import { useApi } from '../hooks/useApi.js';
import { formatPrice } from '../lib/listings.js';

export const ORDER_STATUS_LABEL = { PENDING: 'Awaiting payment', PAID: 'Paid', COMPLETED: 'Completed' };
export const ORDER_STATUS_CLASS = { PENDING: 'badge-pending', PAID: 'badge-paid', COMPLETED: 'badge-done' };

export default function OrderPage() {
  const { id } = useParams();
  const { token } = useAuth();
  const { data, error, loading, reload } = useApi(`/api/orders/${id}`, { token });
  const [updated, setUpdated] = useState(null); // the order after "I received it"
  const [submittedReview, setSubmittedReview] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [confirmError, setConfirmError] = useState(null);

  async function handleConfirm() {
    setConfirming(true);
    setConfirmError(null);
    try {
      setUpdated(await apiRequest(`/api/orders/${id}/confirm`, { method: 'POST', token }));
    } catch (e) {
      setConfirmError(e.message);
    } finally {
      setConfirming(false);
    }
  }

  if (loading) return <Loading label="Loading order…" />;
  if (error) return <ErrorMessage message={error.message} onRetry={error.status === 403 || error.status === 404 ? undefined : reload} />;

  const order = updated ?? data;
  const review = submittedReview ?? order.review;

  return (
    <section>
      <h1>Order #{order.id}</h1>
      <p>
        <span className={`badge ${ORDER_STATUS_CLASS[order.status]}`}>{ORDER_STATUS_LABEL[order.status]}</span>
      </p>
      {order.status === 'PAID' && (
        <p className="success-note" role="status">Payment received. Thank you! Meet {order.seller.fullName} to collect your order.</p>
      )}

      <ul className="line-list">
        {order.items.map((item) => (
          <li key={item.listingId} className="line">
            <Link to={`/listings/${item.listingId}`} className="line-title">{item.title}</Link>
            <span>{formatPrice(item.price)}</span>
          </li>
        ))}
      </ul>
      <p className="total-row">
        <span>Total</span>
        <strong>{formatPrice(order.total)}</strong>
      </p>
      <p className="muted">Seller: {order.seller.fullName}</p>
      {order.paymentReference && <p className="muted">Payment reference: {order.paymentReference}</p>}

      {confirmError && (
        <p className="form-error" role="alert">
          {confirmError}
        </p>
      )}

      <div className="actions">
        {order.status === 'PENDING' && (
          <Link to={`/checkout?order=${order.id}`} className="btn btn-primary">Continue to payment</Link>
        )}
        {order.status === 'PAID' && (
          <button type="button" className="btn btn-primary" onClick={handleConfirm} disabled={confirming}>
            {confirming ? 'Saving…' : 'I received my order'}
          </button>
        )}
        <Link to="/orders" className="btn btn-secondary">All my orders</Link>
      </div>
      {order.status === 'COMPLETED' && <p className="success-note" role="status">Order completed. Enjoy!</p>}

      {order.status === 'COMPLETED' &&
        (review ? (
          <div className="card">
            <h2 className="card-title">Your review of {order.seller.fullName}</h2>
            <p><Stars value={review.rating} /></p>
            {review.comment && <p>{review.comment}</p>}
          </div>
        ) : (
          <ReviewForm orderId={order.id} sellerName={order.seller.fullName} token={token} onSaved={setSubmittedReview} />
        ))}
    </section>
  );
}

/** FR6: after the order is completed the buyer rates the seller (1 to 5) and may add text. One review per order. */
function ReviewForm({ orderId, sellerName, token, onSaved }) {
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setFormError(null);
    if (rating === 0) {
      setErrors({ rating: 'Choose a rating from 1 to 5' });
      return;
    }
    setErrors({});
    setSubmitting(true);
    try {
      onSaved(await apiRequest(`/api/orders/${orderId}/review`, { method: 'POST', token, body: { rating, comment: comment.trim() } }));
    } catch (e) {
      setErrors(e.fieldErrors ?? {});
      setFormError(e.fieldErrors ? null : e.message);
      setSubmitting(false);
    }
  }

  return (
    <form className="card form" onSubmit={handleSubmit} noValidate>
      <h2 className="card-title">Rate {sellerName}</h2>
      <StarInput value={rating} onChange={setRating} error={errors.rating} />
      <FormField
        id="comment"
        label="Your review (optional)"
        as="textarea"
        rows={3}
        maxLength={1000}
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        error={errors.comment}
      />
      {formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}
      <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
        {submitting ? 'Saving…' : 'Submit review'}
      </button>
    </form>
  );
}

// Checkout: turns the cart into an order on the server, then pays it with the simulated gateway.
// /checkout?order=12 continues paying an order that was created earlier (e.g. after a declined card).
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { apiRequest } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { useCart } from '../cart/CartContext.jsx';
import FormField from '../components/FormField.jsx';
import { EmptyState, ErrorMessage, Loading } from '../components/StatusViews.jsx';
import { useApi } from '../hooks/useApi.js';
import { formatPrice } from '../lib/listings.js';

export default function CheckoutPage() {
  const { token } = useAuth();
  const cart = useCart();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const resumeId = params.get('order');

  const existing = useApi(resumeId ? `/api/orders/${resumeId}` : null, { token });
  const [createdOrderId, setCreatedOrderId] = useState(null); // remembered so a retry does not create a second order
  const [card, setCard] = useState('');
  const [cardError, setCardError] = useState(null);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  if (resumeId && existing.loading) return <Loading label="Loading your order…" />;
  if (resumeId && existing.error) return <ErrorMessage message={existing.error.message} onRetry={existing.reload} />;
  if (resumeId && existing.data.status !== 'PENDING') {
    return (
      <EmptyState title="This order is already paid">
        <Link to={`/orders/${resumeId}`} className="btn btn-primary">View order</Link>
      </EmptyState>
    );
  }

  const lines = resumeId ? existing.data.items : cart.items;
  const total = resumeId ? Number(existing.data.total) : cart.total;
  if (lines.length === 0) {
    return (
      <EmptyState title="Your cart is empty">
        <Link to="/" className="btn btn-primary">Browse listings</Link>
      </EmptyState>
    );
  }

  async function handlePay(event) {
    event.preventDefault();
    setError(null);
    setCardError(null);
    if (!card.trim()) {
      setCardError('Enter a card number');
      return;
    }
    setSubmitting(true);
    try {
      let orderId = resumeId ?? createdOrderId;
      if (!orderId) {
        const order = await apiRequest('/api/orders', {
          method: 'POST',
          token,
          body: { listingIds: cart.items.map((item) => item.id) },
        });
        orderId = order.id;
        setCreatedOrderId(orderId);
      }
      await apiRequest(`/api/orders/${orderId}/pay`, { method: 'POST', token, body: { cardNumber: card } });
      cart.clear();
      navigate(`/orders/${orderId}`, { replace: true });
    } catch (e) {
      if (e.fieldErrors?.cardNumber) setCardError(e.fieldErrors.cardNumber);
      else setError({ message: e.message, status: e.status });
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-page">
      <h1>Checkout</h1>
      <ul className="line-list">
        {lines.map((line) => (
          <li key={line.id ?? line.listingId} className="line">
            <span>{line.title}</span>
            <span>{formatPrice(line.price)}</span>
          </li>
        ))}
      </ul>
      <p className="total-row">
        <span>Total</span>
        <strong>{formatPrice(total)}</strong>
      </p>

      <form className="card form" onSubmit={handlePay} noValidate>
        <h2 className="card-title">Pay by card (simulated)</h2>
        <FormField
          id="cardNumber"
          label="Card number"
          inputMode="numeric"
          autoComplete="off"
          placeholder="4242 4242 4242 4242"
          hint="Demo payment: no real money moves. 4242 4242 4242 4242 is approved; 4000 0000 0000 0002 is always declined."
          value={card}
          onChange={(e) => setCard(e.target.value)}
          error={cardError}
        />
        {error && (
          <div className="form-error" role="alert">
            <p>{error.message}</p>
            {(error.status === 409 || error.status === 404) && !resumeId && (
              <p><Link to="/cart">Back to your cart</Link></p>
            )}
          </div>
        )}
        <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Processing payment…' : `Pay ${formatPrice(total)}`}
        </button>
      </form>
    </section>
  );
}

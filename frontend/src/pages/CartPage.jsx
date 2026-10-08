import { Link } from 'react-router';
import { useCart } from '../cart/CartContext.jsx';
import { EmptyState } from '../components/StatusViews.jsx';
import { formatPrice } from '../lib/listings.js';

export default function CartPage() {
  const { items, total, remove, clear } = useCart();

  if (items.length === 0) {
    return (
      <EmptyState title="Your cart is empty">
        <p className="muted">Find something you like and add it to your cart.</p>
        <Link to="/" className="btn btn-primary">Browse listings</Link>
      </EmptyState>
    );
  }

  return (
    <section>
      <h1>Your cart</h1>
      <p className="muted">Items from {items[0].sellerName}</p>
      <ul className="line-list">
        {items.map((item) => (
          <li key={item.id} className="line">
            <div>
              <Link to={`/listings/${item.id}`} className="line-title">{item.title}</Link>
              <p className="muted line-price">{formatPrice(item.price)}</p>
            </div>
            <button type="button" className="btn btn-secondary" onClick={() => remove(item.id)} aria-label={`Remove ${item.title}`}>
              Remove
            </button>
          </li>
        ))}
      </ul>
      <p className="total-row">
        <span>Total</span>
        <strong>{formatPrice(total)}</strong>
      </p>
      <div className="actions">
        <Link to="/checkout" className="btn btn-primary">Checkout</Link>
        <button type="button" className="btn btn-secondary" onClick={clear}>Empty cart</button>
      </div>
    </section>
  );
}

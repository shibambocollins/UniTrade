import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext.jsx';
import { EmptyState, ErrorMessage, Loading } from '../components/StatusViews.jsx';
import { useApi } from '../hooks/useApi.js';
import { formatPrice } from '../lib/listings.js';
import { ORDER_STATUS_CLASS, ORDER_STATUS_LABEL } from './OrderPage.jsx';

export default function OrdersPage() {
  const { token } = useAuth();
  const { data: orders, error, loading, reload } = useApi('/api/orders', { token });

  return (
    <section>
      <h1>My orders</h1>
      {loading && <Loading label="Loading your orders…" />}
      {error && <ErrorMessage message={error.message} onRetry={reload} />}
      {orders && orders.length === 0 && (
        <EmptyState title="You have not bought anything yet">
          <Link to="/" className="btn btn-primary">Browse listings</Link>
        </EmptyState>
      )}
      {orders && orders.length > 0 && (
        <ul className="line-list">
          {orders.map((order) => (
            <li key={order.id} className="line">
              <div>
                <Link to={`/orders/${order.id}`} className="line-title">
                  Order #{order.id} · {order.items.map((item) => item.title).join(', ')}
                </Link>
                <p className="muted line-price">
                  {formatPrice(order.total)} · {order.seller.fullName}
                </p>
              </div>
              <span className={`badge ${ORDER_STATUS_CLASS[order.status]}`}>{ORDER_STATUS_LABEL[order.status]}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

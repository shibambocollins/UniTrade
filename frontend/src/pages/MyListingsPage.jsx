import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext.jsx';
import ListingCard from '../components/ListingCard.jsx';
import { EmptyState, ErrorMessage, Loading } from '../components/StatusViews.jsx';
import { useApi } from '../hooks/useApi.js';

export default function MyListingsPage() {
  const { token } = useAuth();
  const { data: listings, error, loading, reload } = useApi('/api/listings/mine', { token });

  return (
    <section>
      <div className="page-head">
        <h1>My listings</h1>
        <Link to="/listings/new" className="btn btn-primary">Sell something</Link>
      </div>
      {loading && <Loading label="Loading your listings…" />}
      {error && <ErrorMessage message={error.message} onRetry={reload} />}
      {listings && listings.length === 0 && (
        <EmptyState title="You have no listings yet">
          <p className="muted">Items and services you list will appear here.</p>
        </EmptyState>
      )}
      {listings && listings.length > 0 && (
        <div className="listing-grid">
          {listings.map((listing) => (
            <ListingCard key={listing.id} listing={listing} />
          ))}
        </div>
      )}
    </section>
  );
}

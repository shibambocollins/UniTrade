import { useParams } from 'react-router';
import SellerReviews from '../components/SellerReviews.jsx';
import { ErrorMessage, Loading } from '../components/StatusViews.jsx';
import { useApi } from '../hooks/useApi.js';

// Public page about a seller: name, average rating and the reviews they received (FR6).
export default function UserPage() {
  const { id } = useParams();
  const { data, error, loading } = useApi(`/api/users/${id}/reviews`);

  if (loading) return <Loading label="Loading seller…" />;
  if (error) return <ErrorMessage message={error.message} />;

  return (
    <section>
      <h1>{data.seller.fullName}</h1>
      <p className="muted">Seller reviews</p>
      <div className="card">
        <SellerReviews userId={id} />
      </div>
    </section>
  );
}

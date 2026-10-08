// A seller's average rating and the reviews they received. Used on the public seller page and on My profile.
import { useApi } from '../hooks/useApi.js';
import { EmptyState, ErrorMessage, Loading } from './StatusViews.jsx';
import { RatingSummary, Stars } from './Stars.jsx';

export default function SellerReviews({ userId }) {
  const { data, error, loading, reload } = useApi(`/api/users/${userId}/reviews`);

  if (loading) return <Loading label="Loading reviews…" />;
  if (error) return <ErrorMessage message={error.message} onRetry={error.status === 404 ? undefined : reload} />;

  return (
    <div>
      <p className="rating-line">
        <RatingSummary average={data.averageRating} count={data.reviewCount} />
      </p>
      {data.reviews.length === 0 ? (
        <EmptyState title="No reviews yet" />
      ) : (
        <ul className="line-list">
          {data.reviews.map((review) => (
            <li key={review.id} className="review">
              <p>
                <Stars value={review.rating} /> <strong>{review.reviewerName}</strong>{' '}
                <span className="muted">· {new Date(review.createdAt).toLocaleDateString('en-ZA')}</span>
              </p>
              {review.comment && <p>{review.comment}</p>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

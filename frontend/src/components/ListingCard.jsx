import { useState } from 'react';
import { Link } from 'react-router';
import { categoryLabel, conditionLabel, formatPrice, typeLabel } from '../lib/listings.js';

/** Photo with a neutral placeholder when there is no image or it cannot be loaded (e.g. offline). */
export function ListingImage({ listing, className = 'listing-img' }) {
  const [failed, setFailed] = useState(false);
  if (!listing.imageUrl || failed) {
    return (
      <div className={`${className} listing-img-empty`} role="img" aria-label="No photo">
        {listing.type === 'SERVICE' ? 'Service' : 'No photo'}
      </div>
    );
  }
  return (
    <img className={className} src={listing.imageUrl} alt={listing.title} loading="lazy" onError={() => setFailed(true)} />
  );
}

/** One listing in a grid: used by My listings now and by the home feed in Slice 3. */
export default function ListingCard({ listing }) {
  return (
    <Link to={`/listings/${listing.id}`} className="listing-card">
      <ListingImage listing={listing} />
      <div className="listing-card-body">
        <p className="listing-card-title">{listing.title}</p>
        <p className="listing-card-price">{formatPrice(listing.price)}</p>
        <p className="listing-card-meta">
          {categoryLabel(listing.category)} · {typeLabel(listing.type)}
          {listing.condition ? ` · ${conditionLabel(listing.condition)}` : ''}
        </p>
        {listing.status !== 'ACTIVE' && <span className="badge badge-sold">{listing.status}</span>}
      </div>
    </Link>
  );
}

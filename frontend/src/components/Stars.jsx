// Star rating display and input (FR6).

/** Read-only stars, e.g. "★★★★☆". The accessible text says the number, because screen readers skip the symbols. */
export function Stars({ value }) {
  const filled = Math.round(value);
  return (
    <span className="stars" role="img" aria-label={`${value} out of 5 stars`}>
      {'★'.repeat(filled)}
      {'☆'.repeat(5 - filled)}
    </span>
  );
}

/** One line such as "★ 4.3 (3 reviews)" or "No reviews yet". */
export function RatingSummary({ average, count }) {
  if (!count) return <span className="muted">No reviews yet</span>;
  return (
    <span>
      <Stars value={average} /> <strong>{average.toFixed(1)}</strong>{' '}
      <span className="muted">({count} {count === 1 ? 'review' : 'reviews'})</span>
    </span>
  );
}

/** Pick 1 to 5 with radio buttons (works with keyboard and touch). */
export function StarInput({ value, onChange, error }) {
  return (
    <fieldset className="star-input">
      <legend>Your rating</legend>
      <div className="star-options">
        {[1, 2, 3, 4, 5].map((n) => (
          <label key={n} className={value >= n ? 'star star-on' : 'star'}>
            <input type="radio" name="rating" value={n} checked={value === n} onChange={() => onChange(n)} />
            <span aria-hidden="true">{value >= n ? '★' : '☆'}</span>
            <span className="visually-hidden">{n} {n === 1 ? 'star' : 'stars'}</span>
          </label>
        ))}
      </div>
      {error && (
        <p className="field-error" role="alert">
          {error}
        </p>
      )}
    </fieldset>
  );
}

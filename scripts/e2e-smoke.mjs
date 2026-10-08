// End-to-end smoke test: walks through every user flow (FR1-FR6) against a RUNNING backend using real HTTP.
// It creates its own throw-away students (unique emails), so it can be run repeatedly.
//
// Usage (from the repo root, backend already running):
//   node scripts/e2e-smoke.mjs                              # default http://localhost:8080
//   node scripts/e2e-smoke.mjs --api http://localhost:8081  # another port, or the deployed API
//
// It prints one line per check and exits with code 1 if any check fails.
// Note: it really buys things and posts to the board, so prefer a local or demo database.

const apiArg = process.argv.indexOf('--api');
const API = (apiArg > -1 ? process.argv[apiArg + 1] : 'http://localhost:8080').replace(/\/$/, '');
const stamp = Date.now().toString(36);

let failures = 0;
let total = 0;

function check(name, ok, detail = '') {
  total += 1;
  if (!ok) failures += 1;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : `  -> ${detail}`}`);
}

async function call(method, path, { token, body } = {}) {
  const response = await fetch(API + path, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    /* not JSON */
  }
  return { status: response.status, json, text };
}

async function register(name) {
  const email = `${name.toLowerCase()}.${stamp}@mycput.ac.za`;
  const r = await call('POST', '/api/auth/register', { body: { fullName: name, email, password: 'Password123!' } });
  check(`FR1 register ${name}`, r.status === 201 && r.json?.token, `status ${r.status} ${r.text.slice(0, 120)}`);
  return { ...r.json, email };
}

async function main() {
  console.log(`E2E smoke test against ${API}\n`);

  const health = await call('GET', '/api/health');
  check('API and database are up', health.status === 200 && health.json?.database === 'UP', health.text);
  if (health.status !== 200) {
    console.log('\nThe API is not reachable: start the backend first (README section 3).');
    process.exit(1);
  }

  // ---- FR1 authentication
  const bad = await call('POST', '/api/auth/register', { body: { fullName: 'X', email: `x.${stamp}@gmail.com`, password: 'Password123!' } });
  check('FR1 non-student email rejected (400 with field error)', bad.status === 400 && bad.json?.fieldErrors?.email, bad.text.slice(0, 120));
  const lookalike = await call('POST', '/api/auth/register', { body: { fullName: 'X', email: `x@evilmycput.ac.za`, password: 'Password123!' } });
  check('FR1 look-alike domain rejected', lookalike.status === 400, `status ${lookalike.status}`);
  const noToken = await call('GET', '/api/auth/me');
  check('FR1 protected endpoint without token -> 401', noToken.status === 401 && noToken.json?.status === 401, `status ${noToken.status}`);

  const seller = await register('Smoke Seller');
  const buyer = await register('Smoke Buyer');
  const dup = await call('POST', '/api/auth/register', { body: { fullName: 'Dup', email: seller.email.toUpperCase(), password: 'Password123!' } });
  check('FR1 duplicate email -> 409', dup.status === 409, `status ${dup.status}`);
  const wrong = await call('POST', '/api/auth/login', { body: { email: seller.email, password: 'wrong-password' } });
  check('FR1 wrong password -> 401', wrong.status === 401, `status ${wrong.status}`);
  const login = await call('POST', '/api/auth/login', { body: { email: seller.email, password: 'Password123!' } });
  check('FR1 login returns a JWT', login.status === 200 && login.json?.token, `status ${login.status}`);
  const me = await call('GET', '/api/auth/me', { token: login.json?.token });
  check('FR1 token opens /me', me.status === 200 && me.json?.email === seller.email, `status ${me.status}`);

  // ---- FR2 listings
  const listingBody = { title: `Smoke lamp ${stamp}`, description: 'E2E test item', category: 'ELECTRONICS', type: 'GOOD', price: 80, condition: 'USED' };
  const invalid = await call('POST', '/api/listings', { token: seller.token, body: { ...listingBody, title: '', price: -5 } });
  check('FR2 invalid listing rejected (title, price)', invalid.status === 400 && invalid.json?.fieldErrors?.title && invalid.json?.fieldErrors?.price, invalid.text.slice(0, 120));
  const created = await call('POST', '/api/listings', { token: seller.token, body: listingBody });
  check('FR2 create listing', created.status === 201 && created.json?.status === 'ACTIVE', created.text.slice(0, 150));
  const listingId = created.json?.id;
  const other = await call('PUT', `/api/listings/${listingId}`, { token: buyer.token, body: listingBody });
  check('FR2 another student cannot edit (403)', other.status === 403, `status ${other.status}`);
  const edited = await call('PUT', `/api/listings/${listingId}`, { token: seller.token, body: { ...listingBody, price: 75 } });
  check('FR2 owner can edit', edited.status === 200 && Number(edited.json?.price) === 75, edited.text.slice(0, 120));
  const mine = await call('GET', '/api/listings/mine', { token: seller.token });
  check('FR2 My listings shows it', mine.status === 200 && mine.json.some((l) => l.id === listingId), `status ${mine.status}`);

  // ---- FR3 search
  const found = await call('GET', `/api/listings?q=${encodeURIComponent(`SMOKE LAMP ${stamp}`)}&category=ELECTRONICS&maxPrice=100`);
  check('FR3 search is case-insensitive and filters combine', found.status === 200 && found.json?.items?.some((l) => l.id === listingId), found.text.slice(0, 150));
  const paged = await call('GET', '/api/listings?size=5&page=0');
  check('FR3 paged result has our own shape', paged.json && 'items' in paged.json && 'totalPages' in paged.json && !('pageable' in paged.json), paged.text.slice(0, 120));
  const badRange = await call('GET', '/api/listings?minPrice=9&maxPrice=1');
  check('FR3 bad price range -> 400', badRange.status === 400, `status ${badRange.status}`);

  // ---- FR4 cart / order / payment
  const own = await call('POST', '/api/orders', { token: seller.token, body: { listingIds: [listingId] } });
  check('FR4 cannot buy your own listing (403)', own.status === 403, `status ${own.status}`);
  const order = await call('POST', '/api/orders', { token: buyer.token, body: { listingIds: [listingId] } });
  check('FR4 checkout creates a PENDING order with the server-side total', order.status === 201 && order.json?.status === 'PENDING' && Number(order.json?.total) === 75, order.text.slice(0, 150));
  const orderId = order.json?.id;
  const declined = await call('POST', `/api/orders/${orderId}/pay`, { token: buyer.token, body: { cardNumber: '4000 0000 0000 0002' } });
  check('FR4 test card is declined (402) and the order stays PENDING', declined.status === 402, `status ${declined.status}`);
  const paid = await call('POST', `/api/orders/${orderId}/pay`, { token: buyer.token, body: { cardNumber: '4242 4242 4242 4242' } });
  check('FR4 normal card is approved -> PAID', paid.status === 200 && paid.json?.status === 'PAID' && /^MOCK-/.test(paid.json?.paymentReference ?? ''), paid.text.slice(0, 150));
  const sold = await call('GET', `/api/listings/${listingId}`);
  check('FR4 listing is now SOLD', sold.json?.status === 'SOLD', sold.text.slice(0, 100));
  const notListed = await call('GET', `/api/listings?q=${encodeURIComponent(`smoke lamp ${stamp}`)}`);
  check('FR4 sold listing no longer appears in search', notListed.json?.totalItems === 0, notListed.text.slice(0, 100));
  const again = await call('POST', `/api/orders/${orderId}/pay`, { token: buyer.token, body: { cardNumber: '4242 4242 4242 4242' } });
  check('FR4 cannot pay twice (409)', again.status === 409, `status ${again.status}`);
  const lateOrder = await call('POST', '/api/orders', { token: seller.token, body: { listingIds: [listingId] } });
  check('FR4 sold listing cannot be ordered again', lateOrder.status === 403 || lateOrder.status === 409, `status ${lateOrder.status}`);

  // ---- FR6 reviews
  const earlyReview = await call('POST', `/api/orders/${orderId}/review`, { token: buyer.token, body: { rating: 5, comment: 'too early' } });
  check('FR6 cannot review before the order is completed (409)', earlyReview.status === 409, `status ${earlyReview.status}`);
  const confirmed = await call('POST', `/api/orders/${orderId}/confirm`, { token: buyer.token });
  check('FR4 buyer confirms receipt -> COMPLETED', confirmed.status === 200 && confirmed.json?.status === 'COMPLETED', confirmed.text.slice(0, 120));
  const badRating = await call('POST', `/api/orders/${orderId}/review`, { token: buyer.token, body: { rating: 9 } });
  check('FR6 rating must be 1 to 5 (400)', badRating.status === 400, `status ${badRating.status}`);
  const review = await call('POST', `/api/orders/${orderId}/review`, { token: buyer.token, body: { rating: 4, comment: 'Quick hand-over' } });
  check('FR6 buyer reviews the seller', review.status === 201 && review.json?.rating === 4, review.text.slice(0, 120));
  const second = await call('POST', `/api/orders/${orderId}/review`, { token: buyer.token, body: { rating: 1 } });
  check('FR6 only one review per order (409)', second.status === 409, `status ${second.status}`);
  const profile = await call('GET', `/api/users/${seller.user.id}/reviews`);
  check('FR6 seller average rating is public', profile.status === 200 && profile.json?.averageRating === 4 && profile.json?.reviewCount === 1, profile.text.slice(0, 150));
  const detail = await call('GET', `/api/listings/${listingId}`);
  check('FR6 listing detail shows the seller rating', detail.json?.seller?.averageRating === 4, detail.text.slice(0, 150));

  // ---- FR5 bulletin board
  const anon = await call('POST', '/api/bulletin', { body: { title: 'x', body: 'y', category: 'EVENT' } });
  check('FR5 posting needs a login (401)', anon.status === 401, `status ${anon.status}`);
  const post = await call('POST', '/api/bulletin', { token: seller.token, body: { title: `Smoke post ${stamp}`, body: 'Hello campus', category: 'EVENT' } });
  check('FR5 student posts to the board', post.status === 201, post.text.slice(0, 120));
  const board = await call('GET', '/api/bulletin?category=EVENT&size=50');
  check('FR5 board is public and filterable', board.status === 200 && board.json.items.some((p) => p.id === post.json?.id), board.text.slice(0, 100));
  const notMine = await call('DELETE', `/api/bulletin/${post.json?.id}`, { token: buyer.token });
  check('FR5 only the author can delete (403)', notMine.status === 403, `status ${notMine.status}`);
  const deleted = await call('DELETE', `/api/bulletin/${post.json?.id}`, { token: seller.token });
  check('FR5 author deletes their post', deleted.status === 204, `status ${deleted.status}`);

  // ---- Robustness: errors are JSON, never HTML or stack traces
  const unknown = await call('GET', '/api/does-not-exist', { token: seller.token });
  check('Unknown URL -> JSON 404 (no stack trace)', unknown.status === 404 && unknown.json?.status === 404 && !unknown.text.includes('at za.ac'), unknown.text.slice(0, 120));
  const malformed = await fetch(API + '/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{not json' });
  check('Malformed JSON -> JSON 400', malformed.status === 400 && (malformed.headers.get('content-type') ?? '').includes('json'), `status ${malformed.status}`);

  console.log(`\n${total - failures}/${total} checks passed${failures ? `, ${failures} FAILED` : ''}.`);
  process.exit(failures ? 1 : 0);
}

main().catch((error) => {
  console.error(`\nThe smoke test could not finish: ${error.message}`);
  process.exit(1);
});

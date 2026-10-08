import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import App from '../App.jsx';

const jsonResponse = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const bea = { id: 2, fullName: 'Bea Buyer', email: 'bea@mycput.ac.za', createdAt: '2026-10-08T10:00:00Z' };
const completedOrder = (extra = {}) => ({
  id: 31, status: 'COMPLETED', total: 80, createdAt: '2026-10-08T10:00:00Z', paidAt: '2026-10-08T10:05:00Z',
  completedAt: '2026-10-08T11:00:00Z', paymentReference: 'MOCK-OK', seller: { id: 1, fullName: 'Sam Seller' },
  items: [{ listingId: 7, title: 'Desk lamp', price: 80 }], review: null, ...extra,
});
const sellerReviews = {
  seller: { id: 1, fullName: 'Sam Seller', averageRating: 4.5, reviewCount: 2 },
  averageRating: 4.5,
  reviewCount: 2,
  reviews: [
    { id: 2, rating: 4, comment: 'Good seller', reviewerName: 'Oli Other', createdAt: '2026-10-08T12:00:00Z' },
    { id: 1, rating: 5, comment: 'Excellent!', reviewerName: 'Bea Buyer', createdAt: '2026-10-08T11:00:00Z' },
  ],
};

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

function mockApi(routes) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, options = {}) => {
    const handler = routes[`${options.method ?? 'GET'} ${url}`];
    if (!handler) return jsonResponse({ status: 404, message: `unmocked ${url}` }, 404);
    return typeof handler === 'function' ? handler(options) : handler.clone();
  });
}

const loggedIn = (extra = {}) => ({ 'GET /api/auth/me': jsonResponse(bea), ...extra });

describe('FR6 - reviews and seller ratings', () => {
  beforeEach(() => localStorage.setItem('unitrade.token', 'bea-token'));
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('fr6_01 a completed order offers a review form, and a missing rating is explained without calling the server', async () => {
    const fetchMock = mockApi(loggedIn({ 'GET /api/orders/31': jsonResponse(completedOrder()) }));
    const user = userEvent.setup();
    renderAt('/orders/31');

    await user.click(await screen.findByRole('button', { name: /submit review/i }));
    expect(await screen.findByText('Choose a rating from 1 to 5')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([, o]) => o?.method === 'POST')).toBe(false);
  });

  it('fr6_02 submitting a rating and text sends it and then shows the review instead of the form', async () => {
    const saved = { id: 5, rating: 4, comment: 'Quick hand-over', reviewerName: 'Bea Buyer', createdAt: '2026-10-08T12:00:00Z' };
    const fetchMock = mockApi(
      loggedIn({
        'GET /api/orders/31': jsonResponse(completedOrder()),
        'POST /api/orders/31/review': jsonResponse(saved, 201),
      }),
    );
    const user = userEvent.setup();
    renderAt('/orders/31');

    await user.click(await screen.findByRole('radio', { name: '4 stars' }));
    await user.type(screen.getByLabelText(/your review/i), 'Quick hand-over');
    await user.click(screen.getByRole('button', { name: /submit review/i }));

    expect(await screen.findByText(/your review of sam seller/i)).toBeInTheDocument();
    expect(screen.getByText('Quick hand-over')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '4 out of 5 stars' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /submit review/i })).not.toBeInTheDocument();
    const [, options] = fetchMock.mock.calls.find(([, o]) => o?.method === 'POST');
    expect(JSON.parse(options.body)).toEqual({ rating: 4, comment: 'Quick hand-over' });
  });

  it('fr6_03 the server message is shown when the order was already reviewed', async () => {
    mockApi(
      loggedIn({
        'GET /api/orders/31': jsonResponse(completedOrder()),
        'POST /api/orders/31/review': jsonResponse({ status: 409, message: 'You already reviewed this order.' }, 409),
      }),
    );
    const user = userEvent.setup();
    renderAt('/orders/31');
    await user.click(await screen.findByRole('radio', { name: '5 stars' }));
    await user.click(screen.getByRole('button', { name: /submit review/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent('You already reviewed this order.');
  });

  it('fr6_04 an order that already has a review shows it and no form; an unfinished order has no review form', async () => {
    const review = { id: 5, rating: 5, comment: 'Great', reviewerName: 'Bea Buyer', createdAt: '2026-10-08T12:00:00Z' };
    mockApi(
      loggedIn({
        'GET /api/orders/31': jsonResponse(completedOrder({ review })),
        'GET /api/orders/32': jsonResponse(completedOrder({ id: 32, status: 'PAID', completedAt: null })),
      }),
    );
    const { unmount } = renderAt('/orders/31');
    expect(await screen.findByText(/your review of sam seller/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /submit review/i })).not.toBeInTheDocument();
    unmount();

    renderAt('/orders/32');
    expect(await screen.findByRole('button', { name: /i received my order/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /submit review/i })).not.toBeInTheDocument();
  });

  it('fr6_05 the listing page shows the seller\'s average rating and review count, or "No reviews yet"', async () => {
    const lamp = {
      id: 7, title: 'Desk lamp', description: null, category: 'ELECTRONICS', type: 'GOOD', price: 80, condition: 'USED',
      imageUrl: null, status: 'ACTIVE', createdAt: '2026-10-08T10:00:00Z',
      seller: { id: 1, fullName: 'Sam Seller', averageRating: 4.3, reviewCount: 3 },
    };
    mockApi({
      'GET /api/listings/7': jsonResponse(lamp),
      'GET /api/listings/8': jsonResponse({ ...lamp, id: 8, seller: { id: 1, fullName: 'Sam Seller', reviewCount: 0 } }),
    });
    const { unmount } = renderAt('/listings/7');
    expect(await screen.findByText('4.3')).toBeInTheDocument();
    expect(screen.getByText('(3 reviews)')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sam Seller' })).toHaveAttribute('href', '/users/1');
    unmount();

    renderAt('/listings/8');
    expect(await screen.findByText('No reviews yet')).toBeInTheDocument();
  });

  it('fr6_06 the seller page lists the reviews with names, text and the average', async () => {
    mockApi({ 'GET /api/users/1/reviews': jsonResponse(sellerReviews) });
    renderAt('/users/1');

    expect(await screen.findByRole('heading', { name: 'Sam Seller' })).toBeInTheDocument();
    expect(await screen.findByText('Excellent!')).toBeInTheDocument();
    expect(screen.getByText('Good seller')).toBeInTheDocument();
    expect(screen.getByText('Oli Other')).toBeInTheDocument();
    expect(screen.getByText('4.5')).toBeInTheDocument();
    expect(screen.getByText('(2 reviews)')).toBeInTheDocument();
  });

  it('fr6_07 My profile shows the reviews I received, and an unknown seller page shows a friendly message', async () => {
    mockApi(
      loggedIn({
        'GET /api/users/2/reviews': jsonResponse({ ...sellerReviews, seller: { id: 2, fullName: 'Bea Buyer' } }),
        'GET /api/users/99/reviews': jsonResponse({ status: 404, message: 'We could not find that student.' }, 404),
      }),
    );
    const { unmount } = renderAt('/profile');
    expect(await screen.findByText(/reviews about me as a seller/i)).toBeInTheDocument();
    expect(await screen.findByText('Excellent!')).toBeInTheDocument();
    unmount();

    renderAt('/users/99');
    expect(await screen.findByRole('alert')).toHaveTextContent('We could not find that student.');
  });
});

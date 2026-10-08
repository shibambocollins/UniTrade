import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import App from '../App.jsx';

const jsonResponse = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const bea = { id: 2, fullName: 'Bea Buyer', email: 'bea@mycput.ac.za', createdAt: '2026-10-08T10:00:00Z' };
const lamp = {
  id: 7, title: 'Desk lamp', description: 'Bright', category: 'ELECTRONICS', type: 'GOOD', price: 80, condition: 'USED',
  imageUrl: null, status: 'ACTIVE', createdAt: '2026-10-08T10:00:00Z', seller: { id: 1, fullName: 'Sam Seller' },
};
const chair = { ...lamp, id: 8, title: 'Wooden chair', price: 120 };
const otherSellersBook = { ...lamp, id: 9, title: 'Maths book', seller: { id: 5, fullName: 'Someone Else' } };

const orderOf = (extra = {}) => ({
  id: 31, status: 'PENDING', total: 80, createdAt: '2026-10-08T10:00:00Z', paidAt: null, completedAt: null,
  paymentReference: null, seller: { id: 1, fullName: 'Sam Seller' },
  items: [{ listingId: 7, title: 'Desk lamp', price: 80 }], ...extra,
});

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

const calls = (fetchMock, method, url) => fetchMock.mock.calls.filter(([u, o]) => u === url && (o?.method ?? 'GET') === method);
const loggedInAsBea = (extra = {}) => ({ 'GET /api/auth/me': jsonResponse(bea), ...extra });

describe('FR4 - cart, checkout and orders', () => {
  beforeEach(() => localStorage.setItem('unitrade.token', 'bea-token'));
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('fr4_01 Add to cart updates the header count and the cart page shows the item and total', async () => {
    mockApi(loggedInAsBea({ 'GET /api/listings/7': jsonResponse(lamp), 'GET /api/listings/8': jsonResponse(chair) }));
    const user = userEvent.setup();
    renderAt('/listings/7');

    await user.click(await screen.findByRole('button', { name: /add to cart/i }));
    expect(screen.getByText('In your cart')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /cart, 1 item/i })).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: 'View cart' }));
    expect(await screen.findByRole('heading', { name: 'Your cart' })).toBeInTheDocument();
    expect(screen.getByText('Desk lamp')).toBeInTheDocument();
    expect(screen.getAllByText('R80.00').length).toBeGreaterThan(0);
  });

  it('fr4_02 the owner cannot add their own listing and a logged-out visitor is asked to log in', async () => {
    mockApi(loggedInAsBea({ 'GET /api/listings/7': jsonResponse({ ...lamp, seller: { id: 2, fullName: 'Bea Buyer' } }) }));
    const { unmount } = renderAt('/listings/7');
    expect(await screen.findByRole('heading', { name: 'Desk lamp' })).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Edit' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /add to cart/i })).not.toBeInTheDocument();
    unmount();

    localStorage.clear();
    vi.restoreAllMocks();
    mockApi({ 'GET /api/listings/7': jsonResponse(lamp) });
    renderAt('/listings/7');
    expect(await screen.findByRole('link', { name: /log in to buy/i })).toBeInTheDocument();
  });

  it('fr4_03 items from a second seller are refused with an explanation', async () => {
    localStorage.setItem('unitrade.cart', JSON.stringify([{ id: 7, title: 'Desk lamp', price: 80, sellerId: 1, sellerName: 'Sam Seller' }]));
    mockApi(loggedInAsBea({ 'GET /api/listings/9': jsonResponse(otherSellersBook) }));
    const user = userEvent.setup();
    renderAt('/listings/9');

    await user.click(await screen.findByRole('button', { name: /add to cart/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/already has items from Sam Seller/i);
    expect(JSON.parse(localStorage.getItem('unitrade.cart'))).toHaveLength(1);
  });

  it('fr4_04 the cart survives a reload (localStorage), items can be removed, and an empty cart says so', async () => {
    localStorage.setItem(
      'unitrade.cart',
      JSON.stringify([
        { id: 7, title: 'Desk lamp', price: 80, sellerId: 1, sellerName: 'Sam Seller' },
        { id: 8, title: 'Wooden chair', price: 120, sellerId: 1, sellerName: 'Sam Seller' },
      ]),
    );
    mockApi(loggedInAsBea());
    const user = userEvent.setup();
    renderAt('/cart');

    expect(await screen.findByText('Wooden chair')).toBeInTheDocument();
    expect(screen.getByText('R200.00')).toBeInTheDocument(); // total
    await user.click(screen.getByRole('button', { name: /remove desk lamp/i }));
    expect(screen.queryByText('Desk lamp')).not.toBeInTheDocument();
    expect(screen.getByText('R120.00', { selector: 'strong' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /remove wooden chair/i }));
    expect(screen.getByText(/your cart is empty/i)).toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem('unitrade.cart'))).toEqual([]);
  });

  it('fr4_05 checkout creates the order from the ids only, pays it, clears the cart and shows the paid order', async () => {
    localStorage.setItem('unitrade.cart', JSON.stringify([{ id: 7, title: 'Desk lamp', price: 80, sellerId: 1, sellerName: 'Sam Seller' }]));
    const paid = orderOf({ status: 'PAID', paymentReference: 'MOCK-AB12CD34', paidAt: '2026-10-08T10:05:00Z' });
    const fetchMock = mockApi(
      loggedInAsBea({
        'POST /api/orders': jsonResponse(orderOf(), 201),
        'POST /api/orders/31/pay': jsonResponse(paid),
        'GET /api/orders/31': jsonResponse(paid),
      }),
    );
    const user = userEvent.setup();
    renderAt('/checkout');

    await user.type(await screen.findByLabelText('Card number'), '4242 4242 4242 4242');
    await user.click(screen.getByRole('button', { name: /pay r80\.00/i }));

    expect(await screen.findByRole('heading', { name: 'Order #31' })).toBeInTheDocument();
    expect(await screen.findByText(/payment reference: MOCK-AB12CD34/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /i received my order/i })).toBeInTheDocument();
    // only ids go to the server, never a price
    expect(JSON.parse(calls(fetchMock, 'POST', '/api/orders')[0][1].body)).toEqual({ listingIds: [7] });
    expect(JSON.parse(calls(fetchMock, 'POST', '/api/orders/31/pay')[0][1].body)).toEqual({ cardNumber: '4242 4242 4242 4242' });
    expect(JSON.parse(localStorage.getItem('unitrade.cart'))).toEqual([]);
  });

  it('fr4_06 a declined card shows the reason, keeps the cart, and a retry reuses the same order', async () => {
    localStorage.setItem('unitrade.cart', JSON.stringify([{ id: 7, title: 'Desk lamp', price: 80, sellerId: 1, sellerName: 'Sam Seller' }]));
    let payAttempts = 0;
    const fetchMock = mockApi(
      loggedInAsBea({
        'POST /api/orders': jsonResponse(orderOf(), 201),
        'POST /api/orders/31/pay': () => {
          payAttempts += 1;
          return payAttempts === 1
            ? jsonResponse({ status: 402, message: 'The payment was declined by the card issuer. Please try another card.' }, 402)
            : jsonResponse(orderOf({ status: 'PAID', paymentReference: 'MOCK-OK' }));
        },
        'GET /api/orders/31': jsonResponse(orderOf({ status: 'PAID', paymentReference: 'MOCK-OK' })),
      }),
    );
    const user = userEvent.setup();
    renderAt('/checkout');

    await user.type(await screen.findByLabelText('Card number'), '4000 0000 0000 0002');
    await user.click(screen.getByRole('button', { name: /pay r80\.00/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/declined by the card issuer/i);
    expect(JSON.parse(localStorage.getItem('unitrade.cart'))).toHaveLength(1);

    await user.clear(screen.getByLabelText('Card number'));
    await user.type(screen.getByLabelText('Card number'), '4242 4242 4242 4242');
    await user.click(screen.getByRole('button', { name: /pay r80\.00/i }));
    expect(await screen.findByRole('heading', { name: 'Order #31' })).toBeInTheDocument();
    expect(calls(fetchMock, 'POST', '/api/orders')).toHaveLength(1); // no second order was created
    expect(calls(fetchMock, 'POST', '/api/orders/31/pay')).toHaveLength(2);
  });

  it('fr4_07 an item sold to someone else meanwhile is explained, with a link back to the cart', async () => {
    localStorage.setItem('unitrade.cart', JSON.stringify([{ id: 7, title: 'Desk lamp', price: 80, sellerId: 1, sellerName: 'Sam Seller' }]));
    mockApi(
      loggedInAsBea({
        'POST /api/orders': jsonResponse({ status: 409, message: '"Desk lamp" is no longer available. Remove it from your cart.' }, 409),
      }),
    );
    const user = userEvent.setup();
    renderAt('/checkout');

    await user.type(await screen.findByLabelText('Card number'), '4242424242424242');
    await user.click(screen.getByRole('button', { name: /pay r80\.00/i }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/no longer available/i);
    expect(screen.getByRole('link', { name: /back to your cart/i })).toBeInTheDocument();
  });

  it('fr4_08 checkout asks for a card number without calling the server', async () => {
    localStorage.setItem('unitrade.cart', JSON.stringify([{ id: 7, title: 'Desk lamp', price: 80, sellerId: 1, sellerName: 'Sam Seller' }]));
    const fetchMock = mockApi(loggedInAsBea());
    const user = userEvent.setup();
    renderAt('/checkout');

    await user.click(await screen.findByRole('button', { name: /pay r80\.00/i }));
    expect(await screen.findByText('Enter a card number')).toBeInTheDocument();
    expect(calls(fetchMock, 'POST', '/api/orders')).toHaveLength(0);
  });

  it('fr4_09 the buyer confirms receipt and the order becomes Completed', async () => {
    const fetchMock = mockApi(
      loggedInAsBea({
        'GET /api/orders/31': jsonResponse(orderOf({ status: 'PAID', paymentReference: 'MOCK-OK' })),
        'POST /api/orders/31/confirm': jsonResponse(orderOf({ status: 'COMPLETED', paymentReference: 'MOCK-OK', completedAt: '2026-10-08T11:00:00Z' })),
      }),
    );
    const user = userEvent.setup();
    renderAt('/orders/31');

    await user.click(await screen.findByRole('button', { name: /i received my order/i }));
    expect(await screen.findByText(/order completed/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /i received my order/i })).not.toBeInTheDocument();
    expect(calls(fetchMock, 'POST', '/api/orders/31/confirm')).toHaveLength(1);
  });

  it('fr4_10 an unpaid order offers Continue to payment, and My orders shows empty and filled states', async () => {
    mockApi(
      loggedInAsBea({
        'GET /api/orders/31': jsonResponse(orderOf()),
        'GET /api/orders': jsonResponse([orderOf(), orderOf({ id: 30, status: 'COMPLETED' })]),
      }),
    );
    const { unmount } = renderAt('/orders/31');
    expect(await screen.findByRole('link', { name: /continue to payment/i })).toHaveAttribute('href', '/checkout?order=31');
    unmount();

    renderAt('/orders');
    expect(await screen.findByText('Awaiting payment')).toBeInTheDocument();
    expect(screen.getByText('Completed')).toBeInTheDocument();
  });

  it('fr4_11 another student\'s order shows the server message instead of a blank screen', async () => {
    mockApi(loggedInAsBea({ 'GET /api/orders/31': jsonResponse({ status: 403, message: 'This is not your order.' }, 403) }));
    renderAt('/orders/31');
    expect(await screen.findByRole('alert')).toHaveTextContent('This is not your order.');
  });
});

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import App from '../App.jsx';

const jsonResponse = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const jane = { id: 1, fullName: 'Jane Dlamini', email: 'jane@mycput.ac.za', createdAt: '2026-10-08T10:00:00Z' };

const book = {
  id: 7,
  title: 'Calculus textbook',
  description: 'Barely used',
  category: 'TEXTBOOKS',
  type: 'GOOD',
  price: 150,
  condition: 'USED',
  imageUrl: null,
  status: 'ACTIVE',
  createdAt: '2026-10-08T10:00:00Z',
  seller: { id: 1, fullName: 'Jane Dlamini' },
};

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

/** Answers fetch by "METHOD url". A handler may be a Response or a function(options) returning one. */
function mockApi(routes) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, options = {}) => {
    const handler = routes[`${options.method ?? 'GET'} ${url}`];
    if (!handler) return jsonResponse({ status: 404, message: `unmocked ${url}` }, 404);
    return typeof handler === 'function' ? handler(options) : handler.clone();
  });
}

const loggedIn = (extra = {}) => ({ 'GET /api/auth/me': jsonResponse(jane), ...extra });

describe('FR2 - listing screens', () => {
  beforeEach(() => localStorage.setItem('unitrade.token', 'good-token'));
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('fr2_01 My listings shows a friendly empty state with a Sell button', async () => {
    mockApi(loggedIn({ 'GET /api/listings/mine': jsonResponse([]) }));
    renderAt('/my-listings');
    expect(await screen.findByText(/you have no listings yet/i)).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /sell something/i }).length).toBeGreaterThan(0);
  });

  it('fr2_02 My listings shows each listing with its price and a SOLD badge', async () => {
    mockApi(loggedIn({ 'GET /api/listings/mine': jsonResponse([book, { ...book, id: 8, title: 'Old lamp', status: 'SOLD' }]) }));
    renderAt('/my-listings');
    expect(await screen.findByText('Calculus textbook')).toBeInTheDocument();
    expect(screen.getByText('Old lamp')).toBeInTheDocument();
    expect(screen.getAllByText('R150.00')).toHaveLength(2);
    expect(screen.getByText('SOLD')).toBeInTheDocument();
  });

  it('fr2_03 the form checks title, category and price in the browser without calling the server', async () => {
    const fetchMock = mockApi(loggedIn());
    const user = userEvent.setup();
    renderAt('/listings/new');
    await user.click(await screen.findByRole('button', { name: /publish listing/i }));

    expect(await screen.findByText('Title is required')).toBeInTheDocument();
    expect(screen.getByText('Choose a category')).toBeInTheDocument();
    expect(screen.getByText(/enter a price/i)).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([, o]) => o?.method === 'POST')).toBe(false);
  });

  it('fr2_04 creating a service sends no condition and opens the new listing', async () => {
    const created = { ...book, id: 9, title: 'Maths tutoring', type: 'SERVICE', category: 'TUTORING', condition: null };
    const fetchMock = mockApi(
      loggedIn({
        'POST /api/listings': jsonResponse(created, 201),
        'GET /api/listings/9': jsonResponse(created),
      }),
    );
    const user = userEvent.setup();
    renderAt('/listings/new');

    await user.type(await screen.findByLabelText('Title'), 'Maths tutoring');
    await user.selectOptions(screen.getByLabelText(/i am selling/i), 'SERVICE');
    await user.selectOptions(screen.getByLabelText('Category'), 'TUTORING');
    await user.type(screen.getByLabelText(/price/i), '90');
    expect(screen.queryByLabelText('Condition')).not.toBeInTheDocument(); // services have no condition
    await user.click(screen.getByRole('button', { name: /publish listing/i }));

    expect(await screen.findByRole('heading', { name: 'Maths tutoring' })).toBeInTheDocument();
    const [, options] = fetchMock.mock.calls.find(([, o]) => o?.method === 'POST');
    expect(JSON.parse(options.body)).toMatchObject({ title: 'Maths tutoring', type: 'SERVICE', category: 'TUTORING', price: 90, condition: null });
    expect(options.headers.Authorization).toBe('Bearer good-token');
  });

  it('fr2_05 a server validation message is shown under the field it belongs to', async () => {
    mockApi(
      loggedIn({
        'POST /api/listings': jsonResponse(
          { status: 400, message: 'Please check the highlighted fields.', fieldErrors: { imageUrl: 'Image link must start with http:// or https://' } },
          400,
        ),
      }),
    );
    const user = userEvent.setup();
    renderAt('/listings/new');
    await user.type(await screen.findByLabelText('Title'), 'Lamp');
    await user.selectOptions(screen.getByLabelText('Category'), 'ELECTRONICS');
    await user.type(screen.getByLabelText(/price/i), '50');
    await user.click(screen.getByRole('button', { name: /publish listing/i }));

    expect(await screen.findByText(/must start with http/i)).toBeInTheDocument();
  });

  it('fr2_06 only the owner sees Edit and Delete on a listing', async () => {
    mockApi({ 'GET /api/listings/7': jsonResponse(book) }); // nobody logged in? (token is set but /me is not mocked -> 404)
    renderAt('/listings/7');
    expect(await screen.findByRole('heading', { name: 'Calculus textbook' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Edit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
  });

  it('fr2_07 the owner can delete after confirming', async () => {
    const fetchMock = mockApi(
      loggedIn({
        'GET /api/listings/7': jsonResponse(book),
        'DELETE /api/listings/7': new Response(null, { status: 204 }),
        'GET /api/listings/mine': jsonResponse([]),
      }),
    );
    const user = userEvent.setup();
    renderAt('/listings/7');

    expect(await screen.findByRole('link', { name: 'Edit' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Delete' }));
    // nothing is deleted until the owner confirms
    expect(fetchMock.mock.calls.some(([, o]) => o?.method === 'DELETE')).toBe(false);
    await user.click(screen.getByRole('button', { name: /yes, delete/i }));

    expect(await screen.findByText(/you have no listings yet/i)).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url, o]) => url === '/api/listings/7' && o?.method === 'DELETE')).toBe(true);
  });

  it('fr2_08 a listing that does not exist shows a friendly message, not a blank screen', async () => {
    mockApi({ 'GET /api/listings/999': jsonResponse({ status: 404, message: 'This listing does not exist or was removed.' }, 404) });
    renderAt('/listings/999');
    expect(await screen.findByRole('alert')).toHaveTextContent(/does not exist or was removed/i);
  });

  it('fr2_09 the edit form is filled with the current values', async () => {
    mockApi(loggedIn({ 'GET /api/listings/7': jsonResponse(book) }));
    renderAt('/listings/7/edit');
    expect(await screen.findByDisplayValue('Calculus textbook')).toBeInTheDocument();
    expect(screen.getByLabelText(/price/i)).toHaveValue(150);
    expect(screen.getByRole('button', { name: /save changes/i })).toBeInTheDocument();
  });

  it('fr2_10 someone else\'s listing cannot be edited from the screen', async () => {
    mockApi(loggedIn({ 'GET /api/listings/7': jsonResponse({ ...book, seller: { id: 2, fullName: 'Other Person' } }) }));
    renderAt('/listings/7/edit');
    expect(await screen.findByRole('alert')).toHaveTextContent(/only edit your own listings/i);
  });
});

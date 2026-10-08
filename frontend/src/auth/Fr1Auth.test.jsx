import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import App from '../App.jsx';

const jsonResponse = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const jane = { id: 1, fullName: 'Jane Dlamini', email: 'jane@mycput.ac.za', createdAt: '2026-10-08T10:00:00Z' };
const noReviews = { seller: { id: 1, fullName: 'Jane Dlamini' }, averageRating: null, reviewCount: 0, reviews: [] };

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

/** Replaces fetch with a function that answers by URL; records the calls so tests can inspect them. */
function mockApi(routes) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
    const handler = routes[url];
    if (!handler) return jsonResponse({ status: 404, message: 'unmocked ' + url }, 404); // like a real server: JSON error
    return typeof handler === 'function' ? handler() : handler;
  });
}

describe('FR1 - authentication screens', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it('fr1_01 register shows the server message under the email field for a non-student email', async () => {
    mockApi({
      '/api/auth/register': jsonResponse(
        { status: 400, message: 'Please check the highlighted fields.', fieldErrors: { email: 'Use your university email address ending in @mycput.ac.za' } },
        400,
      ),
    });
    const user = userEvent.setup();
    renderAt('/register');

    await user.type(screen.getByLabelText(/full name/i), 'Jane Dlamini');
    await user.type(screen.getByLabelText(/university email/i), 'jane@gmail.com');
    await user.type(screen.getByLabelText(/^password/i), 'Password123!');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText(/ending in @mycput\.ac\.za/i)).toBeInTheDocument();
    expect(localStorage.getItem('unitrade.token')).toBeNull();
  });

  it('fr1_02 register with valid details logs the student in and keeps the token', async () => {
    const fetchMock = mockApi({ '/api/auth/register': jsonResponse({ token: 'jwt-123', user: jane }, 201) });
    const user = userEvent.setup();
    renderAt('/register');

    await user.type(screen.getByLabelText(/full name/i), 'Jane Dlamini');
    await user.type(screen.getByLabelText(/university email/i), 'jane@mycput.ac.za');
    await user.type(screen.getByLabelText(/^password/i), 'Password123!');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    await screen.findByRole('heading', { name: /welcome to unitrade/i }); // wait until the app has moved to the home page
    await user.click(await screen.findByRole('button', { name: /menu/i })); // the logged-in menu holds "Log out"
    expect(await screen.findByRole('button', { name: /log out/i })).toBeInTheDocument();
    expect(localStorage.getItem('unitrade.token')).toBe('jwt-123');
    const [, options] = fetchMock.mock.calls.find(([url]) => url === '/api/auth/register');
    expect(JSON.parse(options.body)).toEqual({ fullName: 'Jane Dlamini', email: 'jane@mycput.ac.za', password: 'Password123!' });
  });

  it('fr1_03 register checks the password length in the browser without calling the server', async () => {
    const fetchMock = mockApi({});
    const user = userEvent.setup();
    renderAt('/register');

    await user.type(screen.getByLabelText(/full name/i), 'Jane');
    await user.type(screen.getByLabelText(/university email/i), 'jane@mycput.ac.za');
    await user.type(screen.getByLabelText(/^password/i), 'short');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    expect(await screen.findByText(/password must be 8 to 72 characters/i)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('fr1_04 login shows a friendly message for a wrong password and stays on the page', async () => {
    mockApi({ '/api/auth/login': jsonResponse({ status: 401, message: 'Incorrect email or password.' }, 401) });
    const user = userEvent.setup();
    renderAt('/login');

    await user.type(screen.getByLabelText(/university email/i), 'jane@mycput.ac.za');
    await user.type(screen.getByLabelText(/^password/i), 'WrongPassword1');
    await user.click(screen.getByRole('button', { name: /^log in$/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password.');
    expect(screen.getByRole('heading', { name: /welcome back/i })).toBeInTheDocument();
  });

  it('fr1_05 login shows an error instead of a blank screen when the server is unreachable', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    const user = userEvent.setup();
    renderAt('/login');

    await user.type(screen.getByLabelText(/university email/i), 'jane@mycput.ac.za');
    await user.type(screen.getByLabelText(/^password/i), 'Password123!');
    await user.click(screen.getByRole('button', { name: /^log in$/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot reach the unitrade server/i);
  });

  it('fr1_06 a protected page sends a logged-out visitor to login, then returns them after login', async () => {
    mockApi({ '/api/auth/login': jsonResponse({ token: 'jwt-123', user: jane }), '/api/users/1/reviews': jsonResponse(noReviews) });
    const user = userEvent.setup();
    renderAt('/profile');

    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeInTheDocument();

    await user.type(screen.getByLabelText(/university email/i), 'jane@mycput.ac.za');
    await user.type(screen.getByLabelText(/^password/i), 'Password123!');
    await user.click(screen.getByRole('button', { name: /^log in$/i }));

    expect(await screen.findByRole('heading', { name: /my profile/i })).toBeInTheDocument();
    expect(screen.getByText('jane@mycput.ac.za')).toBeInTheDocument();
  });

  it('fr1_07 a stored token that the server rejects (401) is discarded and the visitor is logged out', async () => {
    localStorage.setItem('unitrade.token', 'old-token');
    mockApi({ '/api/auth/me': jsonResponse({ status: 401, message: 'Please log in to continue.' }, 401) });
    renderAt('/profile');

    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeInTheDocument();
    expect(localStorage.getItem('unitrade.token')).toBeNull();
  });

  it('fr1_08 a valid stored token restores the session after a refresh, and Log out clears it', async () => {
    localStorage.setItem('unitrade.token', 'good-token');
    const fetchMock = mockApi({ '/api/auth/me': jsonResponse(jane), '/api/users/1/reviews': jsonResponse(noReviews) });
    const user = userEvent.setup();
    renderAt('/profile');

    expect(await screen.findByRole('heading', { name: /my profile/i })).toBeInTheDocument();
    const [, options] = fetchMock.mock.calls.find(([url]) => url === '/api/auth/me');
    expect(options.headers.Authorization).toBe('Bearer good-token');

    await user.click(screen.getByRole('button', { name: /menu/i }));
    await user.click(screen.getByRole('button', { name: /log out/i }));
    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeInTheDocument();
    expect(localStorage.getItem('unitrade.token')).toBeNull();
  });

  it('fr1_09 the password can be shown and hidden', async () => {
    mockApi({});
    const user = userEvent.setup();
    renderAt('/login');

    const password = screen.getByLabelText(/^password/i);
    expect(password).toHaveAttribute('type', 'password');
    await user.click(screen.getByRole('button', { name: /show/i }));
    expect(password).toHaveAttribute('type', 'text');
    await user.click(screen.getByRole('button', { name: /hide/i }));
    expect(password).toHaveAttribute('type', 'password');
  });
});

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import App from '../App.jsx';

const jsonResponse = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const ayesha = { id: 2, fullName: 'Ayesha Daniels', email: 'ayesha@mycput.ac.za', createdAt: '2026-10-08T10:00:00Z' };
const post = (id, title, extra = {}) => ({
  id, title, body: `Details of ${title}`, category: 'EVENT', createdAt: '2026-10-08T10:00:00Z',
  author: { id: 2, fullName: 'Ayesha Daniels' }, ...extra,
});
const pageOf = (items, extra = {}) => ({ items, page: 0, size: 10, totalItems: items.length, totalPages: 1, ...extra });

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

function mockApi(routes) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, options = {}) => {
    const key = `${options.method ?? 'GET'} ${url}`;
    const handler = routes[key] ?? Object.entries(routes).find(([pattern]) => key.startsWith(pattern.replace(/\*$/, '')) && pattern.endsWith('*'))?.[1];
    if (!handler) return jsonResponse({ status: 404, message: `unmocked ${key}` }, 404);
    return typeof handler === 'function' ? handler(options) : handler.clone();
  });
}

const loggedIn = (extra = {}) => ({ 'GET /api/auth/me': jsonResponse(ayesha), ...extra });
const calls = (fetchMock, method, prefix) => fetchMock.mock.calls.filter(([u, o]) => String(u).startsWith(prefix) && (o?.method ?? 'GET') === method);

describe('FR5 - bulletin board', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('fr5_01 anyone can read the board without logging in', async () => {
    mockApi({ 'GET /api/bulletin?*': jsonResponse(pageOf([post(1, 'Clean-up day'), post(2, 'Study group', { category: 'SERVICE' })])) });
    renderAt('/bulletin');
    expect(await screen.findByText('Clean-up day')).toBeInTheDocument();
    expect(screen.getByText('Study group')).toBeInTheDocument();
    expect(screen.getByText('Event')).toBeInTheDocument();
    expect(screen.getByText('Service')).toBeInTheDocument();
  });

  it('fr5_02 choosing a category asks the API for that category', async () => {
    const fetchMock = mockApi({ 'GET /api/bulletin?*': jsonResponse(pageOf([post(1, 'Clean-up day')])) });
    const user = userEvent.setup();
    renderAt('/bulletin');
    await screen.findByText('Clean-up day');

    await user.click(screen.getByRole('button', { name: 'Events' }));
    await vi.waitFor(() => expect(String(calls(fetchMock, 'GET', '/api/bulletin?').at(-1)[0])).toContain('category=EVENT'));
    expect(screen.getByRole('button', { name: 'Events' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('fr5_03 an empty board and a server problem both show a friendly message', async () => {
    mockApi({ 'GET /api/bulletin?*': jsonResponse(pageOf([], { totalItems: 0, totalPages: 0 })) });
    const { unmount } = renderAt('/bulletin');
    expect(await screen.findByText(/no posts here yet/i)).toBeInTheDocument();
    unmount();

    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    renderAt('/bulletin');
    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot reach the unitrade server/i);
  });

  it('fr5_04 posting needs a login: a logged-out visitor is sent to the login page', async () => {
    mockApi({});
    renderAt('/bulletin/new');
    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeInTheDocument();
  });

  it('fr5_05 the post form checks title and text first, then publishes and opens the post', async () => {
    localStorage.setItem('unitrade.token', 'good');
    const created = post(9, 'Free tutoring', { category: 'SERVICE' });
    const fetchMock = mockApi(loggedIn({ 'POST /api/bulletin': jsonResponse(created, 201), 'GET /api/bulletin/9': jsonResponse(created) }));
    const user = userEvent.setup();
    renderAt('/bulletin/new');

    await user.click(await screen.findByRole('button', { name: /publish post/i }));
    expect(await screen.findByText('Title is required')).toBeInTheDocument();
    expect(screen.getByText('Write something in the post')).toBeInTheDocument();
    expect(calls(fetchMock, 'POST', '/api/bulletin')).toHaveLength(0);

    await user.type(screen.getByLabelText('Title'), 'Free tutoring');
    await user.selectOptions(screen.getByLabelText('Category'), 'SERVICE');
    await user.type(screen.getByLabelText('Post'), 'Every Tuesday');
    await user.click(screen.getByRole('button', { name: /publish post/i }));

    expect(await screen.findByRole('heading', { name: 'Free tutoring' })).toBeInTheDocument();
    expect(JSON.parse(calls(fetchMock, 'POST', '/api/bulletin')[0][1].body)).toEqual({ title: 'Free tutoring', body: 'Every Tuesday', category: 'SERVICE' });
  });

  it('fr5_06 only the author sees Delete, and deleting needs a confirmation', async () => {
    localStorage.setItem('unitrade.token', 'good');
    const fetchMock = mockApi(
      loggedIn({
        'GET /api/bulletin/1': jsonResponse(post(1, 'My notice')),
        'DELETE /api/bulletin/1': new Response(null, { status: 204 }),
        'GET /api/bulletin?*': jsonResponse(pageOf([])),
      }),
    );
    const user = userEvent.setup();
    renderAt('/bulletin/1');

    await user.click(await screen.findByRole('button', { name: 'Delete' }));
    expect(calls(fetchMock, 'DELETE', '/api/bulletin/1')).toHaveLength(0); // nothing yet
    await user.click(screen.getByRole('button', { name: /yes, delete/i }));
    expect(await screen.findByText(/no posts here yet/i)).toBeInTheDocument();
    expect(calls(fetchMock, 'DELETE', '/api/bulletin/1')).toHaveLength(1);
  });

  it('fr5_07 another student does not see Delete, and an unknown post shows a friendly message', async () => {
    localStorage.setItem('unitrade.token', 'good');
    mockApi(
      loggedIn({
        'GET /api/bulletin/1': jsonResponse(post(1, 'Not mine', { author: { id: 7, fullName: 'Someone Else' } })),
        'GET /api/bulletin/99': jsonResponse({ status: 404, message: 'This post does not exist or was deleted.' }, 404),
      }),
    );
    const { unmount } = renderAt('/bulletin/1');
    expect(await screen.findByRole('heading', { name: 'Not mine' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    unmount();

    renderAt('/bulletin/99');
    expect(await screen.findByRole('alert')).toHaveTextContent(/does not exist or was deleted/i);
  });
});

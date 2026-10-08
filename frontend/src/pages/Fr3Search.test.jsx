import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import App from '../App.jsx';

const jsonResponse = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

const listing = (id, title, extra = {}) => ({
  id,
  title,
  description: null,
  category: 'ELECTRONICS',
  type: 'GOOD',
  price: 80,
  condition: 'USED',
  imageUrl: null,
  status: 'ACTIVE',
  createdAt: '2026-10-08T10:00:00Z',
  seller: { id: 1, fullName: 'Thabo Nkosi' },
  ...extra,
});

const pageOf = (items, extra = {}) => ({ items, page: 0, size: 12, totalItems: items.length, totalPages: 1, ...extra });

function renderAt(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

/** Answers every listing search with whatever `answer(url)` returns; returns the fetch spy to inspect calls. */
function mockSearch(answer) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => answer(String(url)));
}

const searchUrls = (fetchMock) => fetchMock.mock.calls.map(([url]) => String(url)).filter((url) => url.startsWith('/api/listings?'));
const lastSearch = (fetchMock) => new URL(searchUrls(fetchMock).at(-1), 'http://x').searchParams;

describe('FR3 - browse, search and filter', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  it('fr3_01 the home page lists ACTIVE listings with their price and a count', async () => {
    mockSearch(() => jsonResponse(pageOf([listing(1, 'Desk lamp'), listing(2, 'Casio calculator', { price: 220 })])));
    renderAt();
    expect(await screen.findByText('Desk lamp')).toBeInTheDocument();
    expect(screen.getByText('Casio calculator')).toBeInTheDocument();
    expect(screen.getByText('R220.00')).toBeInTheDocument();
    expect(screen.getByText('2 listings')).toBeInTheDocument();
  });

  it('fr3_02 searching sends the typed word to the API', async () => {
    const fetchMock = mockSearch(() => jsonResponse(pageOf([listing(1, 'Desk lamp')])));
    const user = userEvent.setup();
    renderAt();
    await screen.findByText('Desk lamp');

    await user.type(screen.getByLabelText('Search listings'), 'lamp{Enter}');

    await vi.waitFor(() => expect(lastSearch(fetchMock).get('q')).toBe('lamp'));
    expect(lastSearch(fetchMock).get('page')).toBe('0');
  });

  it('fr3_03 category, type, condition and price filters are all sent to the API', async () => {
    const fetchMock = mockSearch(() => jsonResponse(pageOf([listing(1, 'Desk lamp')])));
    const user = userEvent.setup();
    renderAt();
    await screen.findByText('Desk lamp');

    await user.selectOptions(screen.getByLabelText('Category'), 'ELECTRONICS');
    await user.selectOptions(screen.getByLabelText('Type'), 'GOOD');
    await user.selectOptions(screen.getByLabelText('Condition'), 'USED');
    await user.type(screen.getByLabelText(/min price/i), '50');
    await user.type(screen.getByLabelText(/max price/i), '300');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    await vi.waitFor(() => expect(lastSearch(fetchMock).get('maxPrice')).toBe('300'));
    const sent = lastSearch(fetchMock);
    expect(sent.get('category')).toBe('ELECTRONICS');
    expect(sent.get('type')).toBe('GOOD');
    expect(sent.get('condition')).toBe('USED');
    expect(sent.get('minPrice')).toBe('50');
  });

  it('fr3_04 a minimum price above the maximum is explained and nothing is searched', async () => {
    const fetchMock = mockSearch(() => jsonResponse(pageOf([listing(1, 'Desk lamp')])));
    const user = userEvent.setup();
    renderAt();
    await screen.findByText('Desk lamp');
    const callsBefore = searchUrls(fetchMock).length;

    await user.type(screen.getByLabelText(/min price/i), '500');
    await user.type(screen.getByLabelText(/max price/i), '100');
    await user.click(screen.getByRole('button', { name: 'Search' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/minimum price cannot be more than the maximum/i);
    expect(searchUrls(fetchMock).length).toBe(callsBefore);
  });

  it('fr3_05 no matches shows a friendly empty state with a way to clear the filters', async () => {
    mockSearch((url) => jsonResponse(url.includes('q=zzz') ? pageOf([], { totalItems: 0, totalPages: 0 }) : pageOf([listing(1, 'Desk lamp')])));
    const user = userEvent.setup();
    renderAt();
    await screen.findByText('Desk lamp');

    await user.type(screen.getByLabelText('Search listings'), 'zzz{Enter}');
    expect(await screen.findByText(/no listings match your search/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /clear search and filters/i }));
    expect(await screen.findByText('Desk lamp')).toBeInTheDocument();
  });

  it('fr3_06 an unreachable server shows a message and Try again loads the listings', async () => {
    let calls = 0;
    mockSearch(() => {
      calls += 1;
      if (calls === 1) throw new TypeError('Failed to fetch');
      return jsonResponse(pageOf([listing(1, 'Desk lamp')]));
    });
    const user = userEvent.setup();
    renderAt();

    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot reach the unitrade server/i);
    await user.click(screen.getByRole('button', { name: /try again/i }));
    expect(await screen.findByText('Desk lamp')).toBeInTheDocument();
  });

  it('fr3_07 paging: Next asks for the following page and the position is shown', async () => {
    const fetchMock = mockSearch((url) => {
      const pageIndex = Number(new URL(url, 'http://x').searchParams.get('page'));
      return jsonResponse(pageOf([listing(10 + pageIndex, `Item on page ${pageIndex + 1}`)], { page: pageIndex, totalItems: 25, totalPages: 3 }));
    });
    const user = userEvent.setup();
    renderAt();

    expect(await screen.findByText('Item on page 1')).toBeInTheDocument();
    expect(screen.getByText('Page 1 of 3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByText('Item on page 2')).toBeInTheDocument();
    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument();
    expect(lastSearch(fetchMock).get('page')).toBe('1');
  });

  it('fr3_08 a search in the address bar is applied on load (shareable link)', async () => {
    const fetchMock = mockSearch(() => jsonResponse(pageOf([listing(1, 'Desk lamp')])));
    renderAt('/?q=lamp&category=ELECTRONICS');
    await screen.findByText('Desk lamp');
    expect(lastSearch(fetchMock).get('q')).toBe('lamp');
    expect(lastSearch(fetchMock).get('category')).toBe('ELECTRONICS');
    expect(screen.getByLabelText('Search listings')).toHaveValue('lamp');
  });
});

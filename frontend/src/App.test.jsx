import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import App from './App.jsx';

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

const jsonResponse = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

describe('Slice 0 - app shell', () => {
  afterEach(() => vi.restoreAllMocks());

  it('slice0_01 shows the API and database status from /api/health (page /status)', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ status: 'UP', database: 'UP' }));
    renderAt('/status');
    expect(screen.getByRole('heading', { name: /system status/i })).toBeInTheDocument();
    expect(await screen.findByText(/database:/i)).toBeInTheDocument();
  });

  it('slice0_02 shows a friendly error (not a blank screen) when the server is unreachable', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    renderAt('/status');
    expect(await screen.findByRole('alert')).toHaveTextContent(/cannot reach the unitrade server/i);
  });

  it('slice0_03 shows a not-found page for unknown routes', () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({}));
    renderAt('/does-not-exist');
    expect(screen.getByRole('heading', { name: /page not found/i })).toBeInTheDocument();
  });

  it('slice0_04 shows an error instead of loading forever when the answer is not from the API (HTML page)', async () => {
    // What a static host returns for /api/* when VITE_API_URL is missing: status 200 with an HTML page
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('<!doctype html><title>UniTrade</title>', {
        status: 200,
        headers: { 'Content-Type': 'text/html; charset=utf-8' },
      }),
    );
    renderAt('/status');
    expect(await screen.findByRole('alert')).toHaveTextContent(/unexpected response/i);
  });
});

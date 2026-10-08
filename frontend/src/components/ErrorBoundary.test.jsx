import { render, screen } from '@testing-library/react';
import ErrorBoundary from './ErrorBoundary.jsx';

function Broken() {
  throw new Error('boom');
}

describe('NFR3 - crash protection', () => {
  it('nfr3_02 a screen that crashes while rendering shows a friendly message instead of a blank page', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {}); // React logs the expected crash; keep the test output clean
    render(
      <ErrorBoundary>
        <Broken />
      </ErrorBoundary>,
    );
    expect(screen.getByRole('alert')).toHaveTextContent(/something went wrong showing this page/i);
    expect(screen.getByRole('button', { name: /back to home/i })).toBeInTheDocument();
    vi.restoreAllMocks();
  });
});

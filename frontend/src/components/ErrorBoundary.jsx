// Last line of defence: if any screen crashes while rendering, show a friendly message and a reload button
// instead of a blank page (NFR3). Error boundaries must be class components in React.
import { Component } from 'react';

export default class ErrorBoundary extends Component {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error) {
    console.error('UniTrade screen crashed:', error);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="state state-error" role="alert">
        <p>Something went wrong showing this page.</p>
        <button type="button" className="btn btn-secondary" onClick={() => window.location.assign('/')}>
          Back to home
        </button>
      </div>
    );
  }
}

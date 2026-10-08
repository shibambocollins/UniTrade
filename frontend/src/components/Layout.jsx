import { useEffect, useRef, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthContext.jsx';
import { useCart } from '../cart/CartContext.jsx';
import ErrorBoundary from './ErrorBoundary.jsx';

// The header stays on one row on a phone: brand, Cart, (Sell) and a Menu button that holds the other links.
export default function Layout() {
  const { user, logout } = useAuth();
  const { count } = useCart();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef(null);

  // Close the menu when the page changes, when Escape is pressed, or when something else is tapped
  useEffect(() => setMenuOpen(false), [location.pathname]);
  useEffect(() => {
    if (!menuOpen) return undefined;
    const onKey = (event) => event.key === 'Escape' && setMenuOpen(false);
    const onPointer = (event) => menuRef.current && !menuRef.current.contains(event.target) && setMenuOpen(false);
    document.addEventListener('keydown', onKey);
    document.addEventListener('pointerdown', onPointer);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('pointerdown', onPointer);
    };
  }, [menuOpen]);

  return (
    <div className="app">
      <header className="app-header">
        <Link to="/" className="brand">
          <span className="brand-mark" aria-hidden="true">U</span>
          UniTrade
        </Link>
        <nav className="app-nav" aria-label="Main">
          <Link to="/cart" className="nav-link" aria-label={`Cart, ${count} ${count === 1 ? 'item' : 'items'}`}>
            Cart{count > 0 ? ` (${count})` : ''}
          </Link>
          {user && <Link to="/listings/new" className="nav-link nav-cta">Sell</Link>}
          <div className="menu" ref={menuRef}>
            <button
              type="button"
              className="nav-link nav-button"
              aria-expanded={menuOpen}
              aria-haspopup="true"
              onClick={() => setMenuOpen((open) => !open)}
            >
              Menu ▾
            </button>
            {menuOpen && (
              <div className="menu-panel">
                {user ? (
                  <>
                    <Link to="/profile">{user.fullName.split(' ')[0]} (my profile)</Link>
                    <Link to="/my-listings">My listings</Link>
                    <Link to="/orders">My orders</Link>
                    <Link to="/bulletin">Bulletin board</Link>
                    <button type="button" onClick={logout}>Log out</button>
                  </>
                ) : (
                  <>
                    <Link to="/login">Log in</Link>
                    <Link to="/register">Create account</Link>
                    <Link to="/bulletin">Bulletin board</Link>
                  </>
                )}
              </div>
            )}
          </div>
        </nav>
      </header>
      <main className="app-main">
        {/* keyed by path, so moving to another page clears a crash from the previous one */}
        <ErrorBoundary key={location.pathname}>
          <Outlet />
        </ErrorBoundary>
      </main>
    </div>
  );
}

// The shopping cart lives in the browser only (React state + localStorage), as FR4 specifies.
// The server never trusts it: checkout sends just the listing ids and the server re-reads titles and prices.
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

const CART_KEY = 'unitrade.cart';

function readCart() {
  try {
    const parsed = JSON.parse(localStorage.getItem(CART_KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return []; // missing, corrupted or blocked storage: start with an empty cart
  }
}

const CartContext = createContext(null);

export function CartProvider({ children }) {
  const [items, setItems] = useState(readCart);

  useEffect(() => {
    try {
      localStorage.setItem(CART_KEY, JSON.stringify(items));
    } catch {
      /* ignore: the cart then just will not survive a refresh */
    }
  }, [items]);

  /**
   * Adds a listing. One order can only hold items from one seller (so a review always rates one seller),
   * so a second seller is refused with an explanation.
   */
  const add = useCallback(
    (listing) => {
      if (items.some((item) => item.id === listing.id)) return { ok: true };
      if (items.length > 0 && items[0].sellerId !== listing.seller.id) {
        return {
          ok: false,
          message: `Your cart already has items from ${items[0].sellerName}. Check out or empty your cart before adding items from another seller.`,
        };
      }
      setItems([
        ...items,
        {
          id: listing.id,
          title: listing.title,
          price: listing.price,
          imageUrl: listing.imageUrl,
          sellerId: listing.seller.id,
          sellerName: listing.seller.fullName,
        },
      ]);
      return { ok: true };
    },
    [items],
  );

  const remove = useCallback((id) => setItems((current) => current.filter((item) => item.id !== id)), []);
  const clear = useCallback(() => setItems([]), []);

  const value = useMemo(
    () => ({
      items,
      count: items.length,
      total: items.reduce((sum, item) => sum + Number(item.price), 0),
      has: (id) => items.some((item) => item.id === id),
      add,
      remove,
      clear,
    }),
    [items, add, remove, clear],
  );
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart() {
  const context = useContext(CartContext);
  if (!context) throw new Error('useCart must be used inside <CartProvider>');
  return context;
}

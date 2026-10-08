import { Route, Routes } from 'react-router';
import { AuthProvider } from './auth/AuthContext.jsx';
import { CartProvider } from './cart/CartContext.jsx';
import Layout from './components/Layout.jsx';
import RequireAuth from './components/RequireAuth.jsx';
import HomePage from './pages/HomePage.jsx';
import BulletinFormPage from './pages/BulletinFormPage.jsx';
import BulletinPage from './pages/BulletinPage.jsx';
import BulletinPostPage from './pages/BulletinPostPage.jsx';
import CartPage from './pages/CartPage.jsx';
import CheckoutPage from './pages/CheckoutPage.jsx';
import ListingDetailPage from './pages/ListingDetailPage.jsx';
import ListingFormPage from './pages/ListingFormPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import MyListingsPage from './pages/MyListingsPage.jsx';
import OrderPage from './pages/OrderPage.jsx';
import OrdersPage from './pages/OrdersPage.jsx';
import NotFoundPage from './pages/NotFoundPage.jsx';
import ProfilePage from './pages/ProfilePage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import StatusPage from './pages/StatusPage.jsx';
import UserPage from './pages/UserPage.jsx';

// All screens are rendered inside <Layout> (header + page content).
// AuthProvider makes "who is logged in" available everywhere; pages under <RequireAuth> need a login.
export default function App() {
  return (
    <AuthProvider>
      <CartProvider>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<HomePage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="status" element={<StatusPage />} />
          <Route path="listings/:id" element={<ListingDetailPage />} />
          <Route path="users/:id" element={<UserPage />} />
          <Route path="bulletin" element={<BulletinPage />} />
          <Route path="bulletin/:id" element={<BulletinPostPage />} />
          <Route path="cart" element={<CartPage />} />
          <Route element={<RequireAuth />}>
            <Route path="bulletin/new" element={<BulletinFormPage />} />
            <Route path="checkout" element={<CheckoutPage />} />
            <Route path="orders" element={<OrdersPage />} />
            <Route path="orders/:id" element={<OrderPage />} />
            <Route path="profile" element={<ProfilePage />} />
            <Route path="my-listings" element={<MyListingsPage />} />
            <Route path="listings/new" element={<ListingFormPage />} />
            <Route path="listings/:id/edit" element={<ListingFormPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
      </CartProvider>
    </AuthProvider>
  );
}

import { useAuth } from '../auth/AuthContext.jsx';
import SellerReviews from '../components/SellerReviews.jsx';

// Minimal profile for now (Slice 1). Later slices add "my listings", orders and the seller rating.
export default function ProfilePage() {
  const { user } = useAuth();
  const since = new Date(user.createdAt).toLocaleDateString('en-ZA', { year: 'numeric', month: 'long', day: 'numeric' });

  return (
    <section>
      <h1>My profile</h1>
      <div className="card">
        <p className="profile-name">{user.fullName}</p>
        <p className="muted">{user.email}</p>
        <p className="muted">Member since {since}</p>
      </div>
      <h2 className="section-title">Reviews about me as a seller</h2>
      <div className="card">
        <SellerReviews userId={user.id} />
      </div>
    </section>
  );
}

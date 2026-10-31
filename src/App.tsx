import { useState, useCallback } from 'react';
import type { AppState, Page, User } from './types';
import './styles/global.css';

// ── Pages ──────────────────────────────────────────────────────
import LoginPage        from './pages/LoginPage';
import StudentDashboard from './pages/StudentDashboard';
import EventsPage       from './pages/EventsPage';
import EventDetailPage  from './pages/EventDetailPage';
import MyRegistrations  from './pages/MyRegistrations';
import MyCertificates   from './pages/MyCertificates';
import AdminDashboard   from './pages/AdminDashboard';
import AdminEvents      from './pages/AdminEvents';
import AdminParticipants from './pages/AdminParticipants';
import AdminCertificates from './pages/AdminCertificates';
import AdminPayments     from './pages/AdminPayments';
import Navbar           from './components/Navbar';

// ── App Root ───────────────────────────────────────────────────
export default function App() {
  const [state, setState] = useState<AppState>({
    page: 'login',
    user: null,
    selectedEventId: null,
  });

  const navigate = useCallback((page: Page, extra?: Partial<AppState>) => {
    setState(s => ({ ...s, page, ...extra }));
  }, []);

  const handleLogin = useCallback((user: User) => {
    const home: Page = user.role === 'ADMIN' ? 'admin-dashboard' : 'student-dashboard';
    setState({ page: home, user, selectedEventId: null });
  }, []);

  const handleLogout = useCallback(() => {
    localStorage.removeItem('jwt_token');
    setState({ page: 'login', user: null, selectedEventId: null });
  }, []);

  const { page, user, selectedEventId } = state;

  // ── Login (no layout) ──────────────────────────────────────
  if (!user || page === 'login') {
    return <LoginPage onLogin={handleLogin} />;
  }

  // ── Render page with layout ────────────────────────────────
  const renderPage = () => {
    switch (page) {
      // Student
      case 'student-dashboard':
        return <StudentDashboard user={user} navigate={navigate} />;
      case 'events':
        return <EventsPage user={user} navigate={navigate} />;
      case 'event-detail':
        return <EventDetailPage eventId={selectedEventId!} user={user} navigate={navigate} />;
      case 'my-registrations':
        return <MyRegistrations user={user} navigate={navigate} />;
      case 'my-certificates':
        return <MyCertificates user={user} navigate={navigate} />;
      // Admin
      case 'admin-dashboard':
        return <AdminDashboard user={user} navigate={navigate} />;
      case 'admin-events':
        return <AdminEvents user={user} navigate={navigate} />;
      case 'admin-participants':
        return <AdminParticipants eventId={selectedEventId!} user={user} navigate={navigate} />;
      case 'admin-certificates':
        return <AdminCertificates user={user} navigate={navigate} />;
      case 'admin-payments':
        return <AdminPayments user={user} navigate={navigate} />;
      default:
        return <div style={{ padding: '2rem' }}>Page not found.</div>;
    }
  };

  return (
    <div className="app-shell">
      <Navbar user={user} currentPage={page} navigate={navigate} onLogout={handleLogout} />
      <main className="main-wrap">
        {renderPage()}
      </main>
    </div>
  );
}

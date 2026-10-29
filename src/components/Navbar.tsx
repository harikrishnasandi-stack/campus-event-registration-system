import type { Page, User } from '../types';
import NotificationBell from './NotificationBell';

interface Props {
  user: User;
  currentPage: Page;
  navigate: (page: Page, extra?: Record<string, unknown>) => void;
  onLogout: () => void;
}

const STUDENT_LINKS: { label: string; icon: string; page: Page }[] = [
  { label: 'Dashboard',        icon: '🏠', page: 'student-dashboard' },
  { label: 'Browse Events',    icon: '📅', page: 'events'            },
  { label: 'My Registrations', icon: '📋', page: 'my-registrations'  },
  { label: 'My Certificates',  icon: '🏆', page: 'my-certificates'   },
];

const ADMIN_LINKS: { label: string; icon: string; page: Page }[] = [
  { label: 'Dashboard',    icon: '📊', page: 'admin-dashboard'   },
  { label: 'Events',       icon: '📅', page: 'admin-events'      },
  { label: 'Certificates', icon: '🏆', page: 'admin-certificates'},
];

export default function Navbar({ user, currentPage, navigate, onLogout }: Props) {
  const links = user.role === 'ADMIN' ? ADMIN_LINKS : STUDENT_LINKS;
  const initials = user.name
    .split(' ')
    .map(n => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);

  const isActive = (page: Page) => {
    if (page === 'admin-dashboard' && currentPage === 'admin-participants') return false;
    return currentPage === page || currentPage.startsWith(page.split('-')[0]);
  };

  return (
    <nav className="navbar">
      <div className="navbar-inner">
        {/* Brand */}
        <button
          className="navbar-brand"
          onClick={() => navigate(user.role === 'ADMIN' ? 'admin-dashboard' : 'student-dashboard')}
        >
          <div className="brand-logo">🎓</div>
          <div>
            <div>Campus Events</div>
            <div className="brand-sub">Management System</div>
          </div>
        </button>

        {/* Links */}
        <div className="nav-links">
          {links.map(({ label, icon, page }) => (
            <button
              key={page}
              className={`nav-link${isActive(page) ? ' active' : ''}`}
              onClick={() => navigate(page)}
            >
              {icon} {label}
            </button>
          ))}
        </div>

        {/* User */}
        <div className="nav-right" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          {user.role === 'STUDENT' && <NotificationBell navigate={navigate} />}
          <div className="user-pill">
            <div className="user-avatar">{initials}</div>
            <span style={{ fontWeight: 600, fontSize: '0.85rem' }}>{user.name.split(' ')[0]}</span>
            {user.role === 'ADMIN' && (
              <span className="badge badge-warning" style={{ fontSize: '0.65rem' }}>ADMIN</span>
            )}
          </div>
          <button className="btn-nav-logout" onClick={onLogout}>Logout</button>
        </div>
      </div>
    </nav>
  );
}


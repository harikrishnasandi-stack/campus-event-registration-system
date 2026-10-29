import { useState } from 'react';
import type { User } from '../types';
import { apiLogin, apiRegister } from '../api/client';

interface Props {
  onLogin: (user: User) => void;
}

export default function LoginPage({ onLogin }: Props) {
  const [tab, setTab] = useState<'login' | 'register'>('login');
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [error, setError]       = useState('');
  const [loading, setLoading]   = useState(false);

  const [regForm, setRegForm] = useState({
    name: '', email: '', registerNumber: '',
    department: '', year: '', password: '', confirm: '',
  });
  const [regError, setRegError] = useState('');

  // ── Demo fill helpers ─────────────────────────────────────
  const fillAdmin   = () => { setEmail('admin@campus.edu');   setPassword('Admin@1234');   setError(''); };
  const fillStudent = () => { setEmail('student@campus.edu'); setPassword('Student@1234'); setError(''); };

  // ── Login ─────────────────────────────────────────────────
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const data = await apiLogin(email, password);
      // Persist token for subsequent API calls
      localStorage.setItem('jwt_token', data.token);
      const user: User = {
        id: String(data.userId ?? data.id ?? ''),
        name: data.name,
        email: data.email,
        registerNumber: data.registerNumber,
        department: data.department,
        year: data.year,
        role: data.role,
        token: data.token,
      };
      onLogin(user);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  // ── Register ──────────────────────────────────────────────
  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setRegError('');
    if (regForm.password.length < 8) { setRegError('Password must be at least 8 characters.'); return; }
    if (regForm.password !== regForm.confirm) { setRegError('Passwords do not match.'); return; }

    setLoading(true);
    try {
      const data = await apiRegister({
        registerNumber: regForm.registerNumber,
        name: regForm.name,
        email: regForm.email,
        password: regForm.password,
        department: regForm.department,
        year: regForm.year,
      });
      localStorage.setItem('jwt_token', data.token);
      const user: User = {
        id: String(data.userId ?? data.id ?? ''),
        name: data.name,
        email: data.email,
        registerNumber: data.registerNumber,
        department: data.department,
        year: data.year,
        role: data.role,
        token: data.token,
      };
      onLogin(user);
    } catch (err) {
      setRegError(err instanceof Error ? err.message : 'Registration failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      {/* ── Left hero panel ──────────────────────────────────── */}
      <div className="login-hero">
        <div className="hero-badge">🎓 KARE</div>
        <h1 className="hero-title">
          Your Campus.<br />
          <span>Your Events.</span><br />
          All in one place.
        </h1>
        <p className="hero-desc">
          Discover, register, and track campus events — from tech symposiums to
          cultural fests. Get your participation certificates, all from one portal.
        </p>
      </div>

      {/* ── Right form panel ─────────────────────────────────── */}
      <div className="login-form-wrap">
        <div className="login-card">
          <div className="login-logo">🏫</div>
          <h2 className="login-title">Campus Event Portal</h2>

          {/* Tab switcher */}
          <div className="login-tabs">
            <button
              className={`login-tab${tab === 'login' ? ' active' : ''}`}
              onClick={() => { setTab('login'); setError(''); }}
            >Sign In</button>
            <button
              className={`login-tab${tab === 'register' ? ' active' : ''}`}
              onClick={() => { setTab('register'); setRegError(''); }}
            >Register</button>
          </div>

          {/* Demo credential shortcuts */}
          <div className="demo-pills">
            <span style={{ fontSize: '0.75rem', color: '#64748b' }}>Quick fill:</span>
            <button type="button" className="demo-pill" onClick={fillAdmin}>👤 Admin</button>
            <button type="button" className="demo-pill" onClick={fillStudent}>🎓 Student</button>
          </div>

          {/* ── Login Form ── */}
          {tab === 'login' && (
            <>
              {error && <div className="alert alert-error">⚠️ {error}</div>}
              <form onSubmit={handleLogin}>
                <div className="form-group">
                  <label className="form-label">Email Address</label>
                  <input type="email" className="form-control" required
                    placeholder="you@university.edu"
                    value={email} onChange={e => setEmail(e.target.value)}
                    autoComplete="email"
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Password</label>
                  <input type="password" className="form-control" required
                    placeholder="Enter your password"
                    value={password} onChange={e => setPassword(e.target.value)}
                    autoComplete="current-password"
                  />
                </div>
                <button type="submit" className="btn btn-navy btn-block btn-lg" disabled={loading}
                  style={{ marginTop: '0.5rem' }}>
                  {loading ? '⏳ Signing in…' : '🔐 Sign In'}
                </button>
              </form>
            </>
          )}

          {/* ── Register Form ── */}
          {tab === 'register' && (
            <>
              {regError && <div className="alert alert-error">⚠️ {regError}</div>}
              <form onSubmit={handleRegister}>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Full Name *</label>
                    <input type="text" className="form-control" required placeholder="e.g. Arjun Sharma"
                      value={regForm.name} onChange={e => setRegForm(f => ({ ...f, name: e.target.value }))} />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Register Number *</label>
                    <input type="text" className="form-control" required placeholder="CS2024001"
                      value={regForm.registerNumber} onChange={e => setRegForm(f => ({ ...f, registerNumber: e.target.value }))} />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">Email Address *</label>
                  <input type="email" className="form-control" required placeholder="john@university.edu"
                    value={regForm.email} onChange={e => setRegForm(f => ({ ...f, email: e.target.value }))} />
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Department</label>
                    <select className="form-control" value={regForm.department}
                      onChange={e => setRegForm(f => ({ ...f, department: e.target.value }))}>
                      <option value="">Select…</option>
                      {['Computer Science','Electronics & Communication','Mechanical Engineering',
                        'Civil Engineering','Information Technology','Business Administration',
                        'Mathematics','Physics','Chemistry'].map(d => <option key={d}>{d}</option>)}
                    </select>
                  </div>
                  <div className="form-group">
                    <label className="form-label">Year</label>
                    <select className="form-control" value={regForm.year}
                      onChange={e => setRegForm(f => ({ ...f, year: e.target.value }))}>
                      <option value="">Select…</option>
                      {['1st Year','2nd Year','3rd Year','4th Year'].map(y => <option key={y}>{y}</option>)}
                    </select>
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-group">
                    <label className="form-label">Password *</label>
                    <input type="password" className="form-control" required placeholder="Min 8 characters"
                      value={regForm.password} onChange={e => setRegForm(f => ({ ...f, password: e.target.value }))} />
                  </div>
                  <div className="form-group">
                    <label className="form-label">Confirm Password *</label>
                    <input type="password" className="form-control" required placeholder="Repeat password"
                      value={regForm.confirm} onChange={e => setRegForm(f => ({ ...f, confirm: e.target.value }))} />
                  </div>
                </div>
                <button type="submit" className="btn btn-navy btn-block btn-lg" disabled={loading}>
                  {loading ? '⏳ Creating account…' : '🎓 Create Account'}
                </button>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

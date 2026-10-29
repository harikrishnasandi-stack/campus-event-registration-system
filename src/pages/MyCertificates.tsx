import { useState, useEffect } from 'react';
import type { User, Page } from '../types';
import { apiGetMyCertificates, getCertificateDownloadUrl, type ApiCertificate } from '../api/client';

interface Props {
  user: User;
  navigate: (page: Page) => void;
}

function fmtDate(iso: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
  });
}

function fmtSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function MyCertificates({ navigate }: Props) {
  const [certs, setCerts]     = useState<ApiCertificate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState('');

  useEffect(() => {
    apiGetMyCertificates()
      .then(setCerts)
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const downloadCert = (cert: ApiCertificate) => {
    const token = localStorage.getItem('jwt_token');
    const url = getCertificateDownloadUrl(cert.studentId, cert.eventId);
    // Use fetch to download with auth header
    fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      .then(res => {
        if (!res.ok) throw new Error('Download failed');
        return res.blob();
      })
      .then(blob => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = cert.fileName;
        a.click();
        URL.revokeObjectURL(a.href);
      })
      .catch(() => alert('Download failed. Please try again.'));
  };

  return (
    <>
      <div className="page-header">
        <div className="page-header-inner">
          <div>
            <h1 className="page-title">🏆 My Certificates</h1>
            <p className="page-subtitle">
              {loading ? 'Loading…' : `${certs.length} certificate${certs.length !== 1 ? 's' : ''} available`}
            </p>
          </div>
        </div>
      </div>

      <div className="container page-section" style={{ paddingTop: '1.5rem' }}>
        {loading && (
          <div style={{ textAlign: 'center', padding: '3rem', color: '#64748b' }}>⏳ Loading certificates…</div>
        )}
        {error && <div className="alert alert-error">⚠️ {error}</div>}

        {!loading && !error && certs.length === 0 && (
          <div style={{ textAlign: 'center', padding: '4rem' }}>
            <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>🏆</div>
            <h3 style={{ color: '#1e293b', marginBottom: '0.5rem' }}>No certificates yet</h3>
            <p style={{ color: '#64748b', marginBottom: '1.5rem' }}>
              Participate in completed events to receive your participation certificates.
            </p>
            <button className="btn btn-navy" onClick={() => navigate('events')}>
              🎪 Browse Events
            </button>
          </div>
        )}

        {!loading && !error && certs.length > 0 && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '1.5rem' }}>
            {certs.map(cert => (
              <div key={cert.id} className="card" style={{ padding: '1.5rem' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1rem' }}>
                  <div style={{ fontSize: '2.5rem' }}>📜</div>
                  <div>
                    <div style={{ fontWeight: 700, color: '#1e293b', marginBottom: '0.15rem' }}>
                      {cert.eventTitle ?? `Event #${cert.eventId}`}
                    </div>
                    <div style={{ fontSize: '0.8rem', color: '#64748b' }}>
                      Participation Certificate
                    </div>
                  </div>
                </div>
                <div style={{ borderTop: '1px solid #f1f5f9', paddingTop: '0.75rem', marginBottom: '1rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', color: '#64748b', marginBottom: '0.25rem' }}>
                    <span>📄 {cert.fileName}</span>
                    <span>{fmtSize(cert.fileSize)}</span>
                  </div>
                  <div style={{ fontSize: '0.8rem', color: '#94a3b8' }}>
                    Uploaded on {fmtDate(cert.uploadedAt)}
                  </div>
                </div>
                <button
                  className="btn btn-navy"
                  style={{ width: '100%' }}
                  onClick={() => downloadCert(cert)}
                >
                  ⬇️ Download Certificate
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
}

import { useState, useEffect, useRef } from 'react';
import type { User, Page } from '../types';
import {
  apiGetAdminEvents, apiGetAllCertificates, apiGetEventParticipants,
  apiUploadCertificate, getCertificateDownloadUrl,
  type ApiEvent, type ApiCertificate, type ApiRegistration,
} from '../api/client';

interface Props {
  user: User;
  navigate: (page: Page) => void;
}

function fmtDate(iso: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}
function fmtSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function AdminCertificates({ navigate: _navigate }: Props) {
  const [events, setEvents]           = useState<ApiEvent[]>([]);
  const [certs, setCerts]             = useState<ApiCertificate[]>([]);
  const [loading, setLoading]         = useState(true);
  const [error, setError]             = useState('');

  // Upload modal state
  const [showModal, setShowModal]     = useState(false);
  const [selEventId, setSelEventId]   = useState('');
  const [selStudentId, setSelStudentId] = useState('');
  const [participants, setParticipants] = useState<ApiRegistration[]>([]);
  const [partsLoading, setPartsLoading] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading]     = useState(false);
  const [uploadMsg, setUploadMsg]     = useState('');
  const [uploadError, setUploadError] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const load = () => {
    Promise.all([apiGetAdminEvents(), apiGetAllCertificates()])
      .then(([evs, cs]) => { setEvents(evs as ApiEvent[]); setCerts(cs as ApiCertificate[]); })
      .catch(err => setError(err.message))
      .finally(() => setLoading(false));
  };

  useEffect(load, []);

  // When event changes, load participants
  useEffect(() => {
    if (!selEventId) { setParticipants([]); return; }
    setPartsLoading(true);
    setSelStudentId('');
    apiGetEventParticipants(Number(selEventId))
      .then(ps => setParticipants(ps as ApiRegistration[]))
      .catch(() => setParticipants([]))
      .finally(() => setPartsLoading(false));
  }, [selEventId]);

  const openModal = () => {
    setSelEventId('');
    setSelStudentId('');
    setSelectedFile(null);
    setUploadMsg('');
    setUploadError('');
    setShowModal(true);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.type !== 'application/pdf') {
      setUploadError('Only PDF files are allowed.');
      setSelectedFile(null);
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setUploadError('File size must be under 10 MB.');
      setSelectedFile(null);
      return;
    }
    setUploadError('');
    setSelectedFile(file);
  };

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selEventId || !selStudentId || !selectedFile) {
      setUploadError('Please select an event, student, and PDF file.');
      return;
    }
    setUploading(true);
    setUploadError('');
    setUploadMsg('');
    try {
      const result = await apiUploadCertificate(Number(selStudentId), Number(selEventId), selectedFile);
      setUploadMsg(`✅ Certificate uploaded: ${result.fileName} (${fmtSize(result.fileSize)})`);
      // Refresh the certs list
      apiGetAllCertificates()
        .then(cs => setCerts(cs as ApiCertificate[]))
        .catch(() => {});
      setTimeout(() => setShowModal(false), 1500);
    } catch (err) {
      setUploadError(err instanceof Error ? err.message : 'Upload failed.');
    } finally {
      setUploading(false);
    }
  };

  const downloadCert = (cert: ApiCertificate) => {
    const token = localStorage.getItem('jwt_token');
    const url = getCertificateDownloadUrl(cert.studentId, cert.eventId);
    fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      .then(res => { if (!res.ok) throw new Error(); return res.blob(); })
      .then(blob => {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = cert.fileName;
        a.click();
        URL.revokeObjectURL(a.href);
      })
      .catch(() => alert('Download failed.'));
  };

  return (
    <>
      <div className="page-header">
        <div className="page-header-inner">
          <div>
            <h1 className="page-title">📜 Certificates</h1>
            <p className="page-subtitle">{certs.length} certificate{certs.length !== 1 ? 's' : ''} uploaded</p>
          </div>
          <button className="btn btn-navy" onClick={openModal}>⬆️ Upload Certificate</button>
        </div>
      </div>

      <div className="container page-section" style={{ paddingTop: '1.5rem' }}>
        {loading && <div style={{ color: '#64748b', padding: '2rem 0' }}>⏳ Loading…</div>}
        {error && <div className="alert alert-error">⚠️ {error}</div>}

        {!loading && !error && certs.length === 0 && (
          <div style={{ textAlign: 'center', padding: '4rem', color: '#94a3b8' }}>
            <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>📜</div>
            <h3 style={{ color: '#1e293b', marginBottom: '0.5rem' }}>No certificates uploaded yet</h3>
            <p>Upload PDF certificates for students who participated in completed events.</p>
          </div>
        )}

        {!loading && !error && certs.length > 0 && (
          <div className="card" style={{ overflow: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 700 }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '2px solid #e2e8f0' }}>
                  {['Student', 'Reg No.', 'Event', 'File', 'Size', 'Uploaded', 'Action'].map(h => (
                    <th key={h} style={{ padding: '0.85rem 1rem', textAlign: 'left', fontSize: '0.78rem', color: '#64748b', fontWeight: 700, textTransform: 'uppercase' }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {certs.map(cert => (
                  <tr key={cert.id} style={{ borderBottom: '1px solid #f1f5f9' }}>
                    <td style={{ padding: '0.85rem 1rem', fontWeight: 600, color: '#1e293b' }}>
                      {cert.studentName ?? `Student #${cert.studentId}`}
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontFamily: 'monospace', fontSize: '0.85rem' }}>
                      {cert.registerNumber ?? '—'}
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.85rem', maxWidth: 200 }}>
                      {cert.eventTitle ?? `Event #${cert.eventId}`}
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.82rem', fontFamily: 'monospace' }}>
                      📄 {cert.fileName}
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#94a3b8', fontSize: '0.8rem' }}>
                      {fmtSize(cert.fileSize)}
                    </td>
                    <td style={{ padding: '0.85rem 1rem', color: '#475569', fontSize: '0.8rem', whiteSpace: 'nowrap' }}>
                      {fmtDate(cert.uploadedAt)}
                    </td>
                    <td style={{ padding: '0.85rem 1rem' }}>
                      <button className="btn btn-outline btn-sm" onClick={() => downloadCert(cert)}>
                        ⬇️ Download
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Upload Modal ──────────────────────────────────────── */}
      {showModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem',
        }} onClick={() => setShowModal(false)}>
          <div style={{
            background: '#fff', borderRadius: 12, padding: '2rem', width: '100%', maxWidth: 520,
            maxHeight: '90vh', overflow: 'auto',
          }} onClick={e => e.stopPropagation()}>
            <h2 style={{ marginBottom: '1.5rem', color: '#1e3a8a' }}>⬆️ Upload Certificate</h2>

            {uploadMsg   && <div className="alert alert-success" style={{ marginBottom: '1rem' }}>{uploadMsg}</div>}
            {uploadError && <div className="alert alert-error"   style={{ marginBottom: '1rem' }}>⚠️ {uploadError}</div>}

            <form onSubmit={handleUpload}>
              {/* Event selector */}
              <div className="form-group">
                <label className="form-label">Select Event *</label>
                <select className="form-control" required value={selEventId}
                  onChange={e => setSelEventId(e.target.value)}>
                  <option value="">— Choose event —</option>
                  {events.map(ev => (
                    <option key={ev.id} value={ev.id}>{ev.title} [{ev.status}]</option>
                  ))}
                </select>
              </div>

              {/* Student selector */}
              <div className="form-group">
                <label className="form-label">Select Student *</label>
                <select className="form-control" required value={selStudentId}
                  onChange={e => setSelStudentId(e.target.value)}
                  disabled={!selEventId || partsLoading}>
                  <option value="">
                    {partsLoading ? '⏳ Loading students…'
                      : !selEventId ? '— Select an event first —'
                      : participants.length === 0 ? '— No registrations for this event —'
                      : '— Choose student —'}
                  </option>
                  {participants.map(p => (
                    <option key={p.studentId} value={p.studentId}>
                      {p.studentName ?? `Student #${p.studentId}`}
                      {p.registerNumber ? ` (${p.registerNumber})` : ''}
                    </option>
                  ))}
                </select>
              </div>

              {/* File upload */}
              <div className="form-group">
                <label className="form-label">Certificate PDF * (max 10 MB)</label>
                <input
                  ref={fileInputRef}
                  type="file" accept=".pdf,application/pdf"
                  className="form-control"
                  onChange={handleFileChange}
                  required
                />
                {selectedFile && (
                  <div style={{ fontSize: '0.8rem', color: '#16a34a', marginTop: '0.25rem' }}>
                    ✅ {selectedFile.name} ({fmtSize(selectedFile.size)})
                  </div>
                )}
              </div>

              <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '1.5rem' }}>
                <button type="button" className="btn btn-outline" onClick={() => setShowModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-navy" disabled={uploading}>
                  {uploading ? '⏳ Uploading…' : '⬆️ Upload Certificate'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}

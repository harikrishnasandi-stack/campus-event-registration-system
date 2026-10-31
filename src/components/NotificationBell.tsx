import { useState, useEffect, useRef, useCallback } from 'react';
import {
  apiGetMyNotifications,
  apiGetUnreadNotificationCount,
  apiMarkNotificationAsRead,
  apiMarkAllNotificationsAsRead,
  type ApiNotification,
} from '../api/client';
import type { Page } from '../types';

interface Props {
  navigate: (page: Page, extra?: Record<string, unknown>) => void;
}

const isItemRead = (n: ApiNotification): boolean =>
  Boolean(n.isRead === true || n.read === true || (n as any).is_read === true || (n as any).is_read === 1 || (n as any).isRead === 1);

export default function NotificationBell({ navigate }: Props) {
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [notifications, setNotifications] = useState<ApiNotification[]>([]);
  const [isOpen, setIsOpen] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(false);
  const [markingAll, setMarkingAll] = useState<boolean>(false);

  // Set of notification IDs currently being marked as read in-flight to prevent duplicate requests
  const pendingIdsRef = useRef<Set<number>>(new Set());
  const dropdownRef = useRef<HTMLDivElement>(null);

  const fetchUnreadCount = useCallback(async () => {
    try {
      const data = await apiGetUnreadNotificationCount();
      if (typeof data.unreadCount === 'number') {
        setUnreadCount(data.unreadCount);
      }
    } catch {
      // Ignore background poll errors
    }
  }, []);

  const loadNotifications = useCallback(async () => {
    setLoading(true);
    try {
      const data = await apiGetMyNotifications();
      setNotifications(data);
      const unread = data.filter(n => !isItemRead(n)).length;
      setUnreadCount(unread);
    } catch (err) {
      console.error('Failed to load notifications', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUnreadCount();

    // Listen for broadcast events from other actions (e.g. event creation or external read updates)
    const handleRefresh = () => {
      fetchUnreadCount();
      if (isOpen) {
        loadNotifications();
      }
    };
    window.addEventListener('notification-refresh', handleRefresh);
    window.addEventListener('notifications-updated', handleRefresh);

    // Poll unread count every 30 seconds while logged in
    const interval = setInterval(() => {
      if (!isOpen) {
        fetchUnreadCount();
      }
    }, 30000);

    return () => {
      clearInterval(interval);
      window.removeEventListener('notification-refresh', handleRefresh);
      window.removeEventListener('notifications-updated', handleRefresh);
    };
  }, [isOpen, fetchUnreadCount, loadNotifications]);

  // Close dropdown on click outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isOpen]);

  const toggleDropdown = () => {
    if (!isOpen) {
      loadNotifications();
    }
    setIsOpen(prev => !prev);
  };

  const handleMarkAsRead = async (id: number, e: React.MouseEvent) => {
    e.stopPropagation();

    // Prevent duplicate API requests if already pending or already read
    if (pendingIdsRef.current.has(id)) return;
    const target = notifications.find(n => n.id === id);
    if (!target || isItemRead(target)) return;

    pendingIdsRef.current.add(id);

    // 1. IMMEDIATELY update UI optimistically (no page refresh required)
    setNotifications(prev =>
      prev.map(n => (n.id === id ? { ...n, isRead: true, read: true } : n))
    );
    setUnreadCount(prev => Math.max(0, prev - 1));

    try {
      // 2. Perform database update via backend API
      const res = await apiMarkNotificationAsRead(id);

      // 3. Reconcile with exact server unread count if available
      if (typeof res.unreadCount === 'number') {
        setUnreadCount(res.unreadCount);
      }

      // Notify other components (like StudentDashboard banner) immediately
      window.dispatchEvent(new CustomEvent('notifications-updated', { detail: { id, isRead: true } }));
    } catch (err) {
      console.error('Failed to mark notification as read on server', err);
      // Rollback optimistic update on server error
      setNotifications(prev =>
        prev.map(n => (n.id === id ? { ...n, isRead: false, read: false } : n))
      );
      setUnreadCount(prev => prev + 1);
    } finally {
      pendingIdsRef.current.delete(id);
    }
  };

  const handleMarkAllAsRead = async () => {
    if (markingAll || unreadCount === 0) return;
    setMarkingAll(true);

    // Save previous state for rollback if needed
    const prevNotifications = [...notifications];
    const prevUnreadCount = unreadCount;

    // 1. IMMEDIATELY mark all notifications as read in UI optimistically
    setNotifications(prev => prev.map(n => ({ ...n, isRead: true, read: true })));
    setUnreadCount(0);

    try {
      // 2. Perform batch database update via backend API
      const res = await apiMarkAllNotificationsAsRead();
      setUnreadCount(res.unreadCount ?? 0);

      // Notify other components immediately
      window.dispatchEvent(new CustomEvent('notifications-updated', { detail: { allRead: true } }));
    } catch (err) {
      console.error('Failed to mark all as read on server', err);
      // Rollback on server error
      setNotifications(prevNotifications);
      setUnreadCount(prevUnreadCount);
    } finally {
      setMarkingAll(false);
    }
  };

  const handleNotificationClick = async (n: ApiNotification) => {
    if (!isItemRead(n)) {
      if (!pendingIdsRef.current.has(n.id)) {
        pendingIdsRef.current.add(n.id);
        // Optimistic update
        setNotifications(prev =>
          prev.map(item => (item.id === n.id ? { ...item, isRead: true, read: true } : item))
        );
        setUnreadCount(prev => Math.max(0, prev - 1));

        try {
          const res = await apiMarkNotificationAsRead(n.id);
          if (typeof res.unreadCount === 'number') {
            setUnreadCount(res.unreadCount);
          }
          window.dispatchEvent(new CustomEvent('notifications-updated', { detail: { id: n.id, isRead: true } }));
        } catch {
          // Ignore navigation click error
        } finally {
          pendingIdsRef.current.delete(n.id);
        }
      }
    }
    setIsOpen(false);
    if (n.eventId) {
      navigate('event-detail', { selectedEventId: String(n.eventId) });
    }
  };

  return (
    <div style={{ position: 'relative' }} ref={dropdownRef}>
      {/* Bell Button */}
      <button
        type="button"
        onClick={toggleDropdown}
        style={{
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          padding: '6px 10px',
          borderRadius: '50%',
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: '1.25rem',
          color: '#1e3a8a',
          outline: 'none',
        }}
        title="Notifications"
        aria-label="Event notifications"
      >
        🔔
        {unreadCount > 0 && (
          <span
            style={{
              position: 'absolute',
              top: '2px',
              right: '2px',
              background: '#dc2626',
              color: '#ffffff',
              borderRadius: '9999px',
              fontSize: '0.65rem',
              fontWeight: 800,
              minWidth: '18px',
              height: '18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '0 4px',
              boxShadow: '0 1px 3px rgba(0,0,0,0.2)',
            }}
          >
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {/* Dropdown Panel */}
      {isOpen && (
        <div
          style={{
            position: 'absolute',
            right: 0,
            top: 'calc(100% + 8px)',
            width: '380px',
            maxWidth: '92vw',
            background: '#ffffff',
            borderRadius: '12px',
            boxShadow: '0 10px 30px rgba(15, 23, 42, 0.15), 0 1px 3px rgba(15, 23, 42, 0.08)',
            border: '1px solid #e2e8f0',
            zIndex: 1050,
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column',
            maxHeight: '480px',
          }}
        >
          {/* Header */}
          <div
            style={{
              padding: '12px 16px',
              background: '#f8fafc',
              borderBottom: '1px solid #e2e8f0',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ fontWeight: 700, fontSize: '0.9rem', color: '#1e293b' }}>
                Notifications
              </span>
              {unreadCount > 0 && (
                <span
                  style={{
                    background: '#fee2e2',
                    color: '#991b1b',
                    fontSize: '0.72rem',
                    fontWeight: 700,
                    padding: '1px 8px',
                    borderRadius: '9999px',
                  }}
                >
                  {unreadCount} new
                </span>
              )}
            </div>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={handleMarkAllAsRead}
                disabled={markingAll}
                style={{
                  background: 'none',
                  border: 'none',
                  color: markingAll ? '#94a3b8' : '#2563eb',
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  cursor: markingAll ? 'not-allowed' : 'pointer',
                  padding: 0,
                }}
              >
                {markingAll ? 'Marking read…' : 'Mark all read'}
              </button>
            )}
          </div>

          {/* Notification List */}
          <div style={{ overflowY: 'auto', flex: 1 }}>
            {loading ? (
              <div style={{ padding: '24px', textAlign: 'center', color: '#64748b', fontSize: '0.85rem' }}>
                ⏳ Loading notifications…
              </div>
            ) : notifications.length === 0 ? (
              <div style={{ padding: '32px 16px', textAlign: 'center' }}>
                <div style={{ fontSize: '2rem', marginBottom: '8px' }}>🎉</div>
                <div style={{ fontWeight: 600, color: '#334155', fontSize: '0.9rem' }}>
                  All caught up!
                </div>
                <div style={{ color: '#94a3b8', fontSize: '0.8rem', marginTop: '4px' }}>
                  No notifications at the moment.
                </div>
              </div>
            ) : (
              notifications.map(n => {
                const itemRead = isItemRead(n);
                const isAnnouncement = n.notificationType === 'EVENT_ANNOUNCEMENT';
                const emoji = isAnnouncement ? '📢' : n.notificationType === 'REGISTRATION_DEADLINE' ? '⏰' : '🔔';
                const displayTitle = n.title || (isAnnouncement ? 'New Event Announcement!' : 'Event Deadline Reminder');

                return (
                  <div
                    key={n.id}
                    onClick={() => handleNotificationClick(n)}
                    style={{
                      padding: '12px 16px',
                      borderBottom: '1px solid #f1f5f9',
                      background: itemRead ? '#ffffff' : '#f0f9ff',
                      cursor: 'pointer',
                      transition: 'background 0.15s ease',
                      display: 'flex',
                      gap: '12px',
                      alignItems: 'flex-start',
                    }}
                    onMouseEnter={e => {
                      e.currentTarget.style.background = itemRead ? '#f8fafc' : '#e0f2fe';
                    }}
                    onMouseLeave={e => {
                      e.currentTarget.style.background = itemRead ? '#ffffff' : '#f0f9ff';
                    }}
                  >
                    <div style={{ fontSize: '1.25rem', marginTop: '2px', flexShrink: 0 }}>{emoji}</div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '8px' }}>
                        <div>
                          <div
                            style={{
                              fontWeight: itemRead ? 600 : 700,
                              fontSize: '0.85rem',
                              color: isAnnouncement ? '#1e3a8a' : '#1e293b',
                              display: 'flex',
                              alignItems: 'center',
                              gap: '6px',
                              flexWrap: 'wrap',
                            }}
                          >
                            <span>{displayTitle}</span>
                            {n.eventTitle && (
                              <span
                                style={{
                                  background: isAnnouncement ? '#e0e7ff' : '#f1f5f9',
                                  color: isAnnouncement ? '#3730a3' : '#475569',
                                  padding: '1px 6px',
                                  borderRadius: '6px',
                                  fontSize: '0.72rem',
                                  fontWeight: 600,
                                }}
                              >
                                {n.eventTitle}
                              </span>
                            )}
                          </div>
                        </div>
                        {!itemRead && (
                          <span
                            style={{
                              width: '8px',
                              height: '8px',
                              borderRadius: '50%',
                              background: '#2563eb',
                              display: 'inline-block',
                              marginTop: '4px',
                              flexShrink: 0,
                            }}
                          />
                        )}
                      </div>
                      <p
                        style={{
                          margin: '4px 0 6px 0',
                          fontSize: '0.78rem',
                          color: '#475569',
                          lineHeight: 1.45,
                          wordBreak: 'break-word',
                        }}
                      >
                        {n.message}
                      </p>
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          fontSize: '0.7rem',
                          color: '#94a3b8',
                        }}
                      >
                        <span>
                          {new Date(n.createdAt).toLocaleDateString('en-IN', {
                            month: 'short',
                            day: 'numeric',
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </span>
                        {!itemRead && (
                          <button
                            type="button"
                            onClick={e => handleMarkAsRead(n.id, e)}
                            style={{
                              background: '#e2e8f0',
                              border: 'none',
                              borderRadius: '4px',
                              padding: '2px 8px',
                              fontSize: '0.7rem',
                              color: '#334155',
                              cursor: 'pointer',
                              fontWeight: 600,
                            }}
                            title="Mark as read"
                          >
                            Mark read
                          </button>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
}

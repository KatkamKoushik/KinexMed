import React from 'react';
import type { SessionSummary } from '../types/session';
import { Calendar } from 'lucide-react';

interface HistoryViewProps {
  sessions: SessionSummary[];
  onSelectSession: (id: string) => void;
}

export const HistoryView: React.FC<HistoryViewProps> = ({
  sessions,
  onSelectSession
}) => {
  if (sessions.length === 0) {
    return (
      <div className="glass-card" style={{ padding: '3rem', textAlign: 'center', color: '#64748B' }}>
        <h3>No Patient History</h3>
        <p>Sessions recorded from patient mobile devices will build a longitudinal timeline here.</p>
      </div>
    );
  }

  // Group sessions by date string (YYYY-MM-DD)
  const grouped: { [date: string]: SessionSummary[] } = {};
  sessions.forEach((s) => {
    const d = new Date(s.created_at || s.started_at).toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
    if (!grouped[d]) grouped[d] = [];
    grouped[d].push(s);
  });

  return (
    <div className="history-page">
      <div className="page-header">
        <div>
          <h2>Patient Rehabilitation History</h2>
          <p style={{ color: '#94A3B8', fontSize: '0.85rem' }}>
            Chronological log of completed therapy sessions and cumulative compliance.
          </p>
        </div>
      </div>

      <div className="timeline-container">
        {Object.entries(grouped).map(([dateStr, daySessions]) => {
          const dayValidReps = daySessions.reduce((acc, s) => acc + s.valid_reps, 0);
          const dayTotalReps = daySessions.reduce((acc, s) => acc + s.total_reps, 0);
          const dayDuration = daySessions.reduce((acc, s) => acc + s.duration_seconds, 0);

          return (
            <div key={dateStr} className="timeline-group">
              <div className="timeline-date-marker">
                <Calendar size={16} color="#06B6D4" />
                <span>{dateStr}</span>
                <span className="timeline-day-summary">
                  {daySessions.length} session(s) · {dayValidReps}/{dayTotalReps} valid reps · {Math.round(dayDuration)}s
                </span>
              </div>

              <div className="timeline-items-wrapper">
                {daySessions.map((session) => {
                  const adherence = session.total_reps > 0
                    ? Math.round((session.valid_reps / session.total_reps) * 100)
                    : 0;

                  return (
                    <div
                      key={session.id}
                      className="glass-card timeline-session-card"
                      onClick={() => onSelectSession(session.id)}
                    >
                      <div className="timeline-card-header">
                        <span className="exercise-title">{session.exercise_name.toUpperCase()}</span>
                        <span className="timestamp-text">
                          {new Date(session.created_at || session.started_at).toLocaleTimeString(undefined, {
                            hour: '2-digit',
                            minute: '2-digit'
                          })}
                        </span>
                      </div>

                      <div className="timeline-card-stats">
                        <div>
                          <span className="stat-label">Valid Reps</span>
                          <span className="stat-value" style={{ color: '#10B981' }}>
                            {session.valid_reps} / {session.total_reps}
                          </span>
                        </div>
                        <div>
                          <span className="stat-label">Adherence</span>
                          <span className="stat-value">{adherence}%</span>
                        </div>
                        <div>
                          <span className="stat-label">Avg Peak ROM</span>
                          <span className="stat-value" style={{ color: '#06B6D4' }}>
                            {Math.round(session.avg_peak_knee_angle)}°
                          </span>
                        </div>
                        <div>
                          <span className="stat-label">Duration</span>
                          <span className="stat-value">{Math.round(session.duration_seconds)}s</span>
                        </div>
                      </div>

                      <div className="timeline-card-footer">
                        <span style={{ fontSize: '0.75rem', color: '#64748B' }}>Device: {session.device_id}</span>
                        <span className="btn-link">Inspect Details →</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

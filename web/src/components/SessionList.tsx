import React from 'react';
import type { SessionSummary } from '../types/session';

interface SessionListProps {
  sessions: SessionSummary[];
  selectedId: string | null;
  onSelectSession: (id: string) => void;
}

export const SessionList: React.FC<SessionListProps> = ({
  sessions,
  selectedId,
  onSelectSession
}) => {
  return (
    <div className="session-list-panel">
      <div className="panel-header">
        <h3 className="panel-title">Session History ({sessions.length})</h3>
      </div>

      <div className="session-cards-container">
        {sessions.map((session) => {
          const isSelected = session.id === selectedId;
          const formattedDate = new Date(session.created_at || session.started_at).toLocaleString(undefined, {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
          });

          return (
            <div
              key={session.id}
              className={`session-item-card ${isSelected ? 'active' : ''}`}
              onClick={() => onSelectSession(session.id)}
            >
              <div className="session-item-top">
                <span className="session-date">{formattedDate}</span>
                <span className="session-badge">
                  {session.valid_reps} / {session.total_reps} Valid
                </span>
              </div>

              <div className="session-stats-row">
                <div className="stat-item">
                  <span className="stat-label">Exercise</span>
                  <span className="stat-val" style={{ textTransform: 'capitalize' }}>
                    {session.exercise_name}
                  </span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">Peak ROM</span>
                  <span className="stat-val" style={{ color: '#38BDF8' }}>
                    {Math.round(session.avg_peak_knee_angle)}°
                  </span>
                </div>
                <div className="stat-item">
                  <span className="stat-label">Duration</span>
                  <span className="stat-val">
                    {Math.round(session.duration_seconds)}s
                  </span>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

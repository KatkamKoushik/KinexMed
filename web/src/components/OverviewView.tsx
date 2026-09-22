import React from 'react';
import type { SessionSummary } from '../types/session';
import { KpiCards } from './KpiCards';
import {
  TrendingUp,
  AlertCircle,
  Clock,
  ArrowRight,
  Activity,
  CheckCircle2
} from 'lucide-react';

interface OverviewViewProps {
  sessions: SessionSummary[];
  onSelectSession: (id: string) => void;
  onViewAllSessions: () => void;
}

export const OverviewView: React.FC<OverviewViewProps> = ({
  sessions,
  onSelectSession,
  onViewAllSessions
}) => {
  const latestSession = sessions.length > 0 ? sessions[0] : null;
  const hasMultipleSessions = sessions.length >= 2;

  // Real chronological trend calculation
  let romDelta = 0;
  let adherenceDelta = 0;

  if (hasMultipleSessions) {
    const oldest = sessions[sessions.length - 1];
    const newest = sessions[0];

    romDelta = Math.round(newest.avg_peak_knee_angle - oldest.avg_peak_knee_angle);
    const oldestAdherence = oldest.total_reps > 0 ? (oldest.valid_reps / oldest.total_reps) * 100 : 0;
    const newestAdherence = newest.total_reps > 0 ? (newest.valid_reps / newest.total_reps) * 100 : 0;
    adherenceDelta = Math.round(newestAdherence - oldestAdherence);
  }

  return (
    <div className="overview-container">
      {/* 1. Primary KPIs */}
      <KpiCards sessions={sessions} />

      {/* 2. Middle Row: Latest Session Spotlight + Trend Evaluation */}
      <div className="overview-grid">
        {/* Latest Session Spotlight */}
        <div className="glass-card spotlight-card">
          <div className="section-card-header">
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Activity size={18} color="#06B6D4" />
              <h3>Latest Verified Session</h3>
            </div>
            {latestSession && (
              <span className="synced-badge">
                <CheckCircle2 size={12} /> Live Sync
              </span>
            )}
          </div>

          {latestSession ? (
            <div className="spotlight-content">
              <div className="spotlight-hero">
                <div className="spotlight-exercise">
                  <h4>{latestSession.exercise_name.toUpperCase()}</h4>
                  <span className="spotlight-date">
                    {new Date(latestSession.created_at || latestSession.started_at).toLocaleString(undefined, {
                      dateStyle: 'medium',
                      timeStyle: 'short'
                    })}
                  </span>
                </div>
                <div className="spotlight-device">
                  Device: <strong>{latestSession.device_id}</strong>
                </div>
              </div>

              <div className="spotlight-metrics-grid">
                <div className="metric-box">
                  <span className="metric-label">Repetitions</span>
                  <span className="metric-val" style={{ color: '#10B981' }}>
                    {latestSession.valid_reps} / {latestSession.total_reps} Valid
                  </span>
                  <span className="metric-sub">
                    {latestSession.total_reps > 0
                      ? `${Math.round((latestSession.valid_reps / latestSession.total_reps) * 100)}% adherence`
                      : '0 attempts'}
                  </span>
                </div>

                <div className="metric-box">
                  <span className="metric-label">Peak Flexion Depth</span>
                  <span className="metric-val" style={{ color: '#06B6D4' }}>
                    {Math.round(latestSession.avg_peak_knee_angle)}°
                  </span>
                  <span className="metric-sub">Target: ≤100°</span>
                </div>

                <div className="metric-box">
                  <span className="metric-label">Duration</span>
                  <span className="metric-val">
                    {Math.round(latestSession.duration_seconds)}s
                  </span>
                  <span className="metric-sub">Active movement</span>
                </div>

                <div className="metric-box">
                  <span className="metric-label">Evidence Flags</span>
                  <span className="metric-val" style={{ color: latestSession.evidence_failure_count > 0 ? '#F59E0B' : '#64748B' }}>
                    {latestSession.evidence_failure_count}
                  </span>
                  <span className="metric-sub">Positioning warnings</span>
                </div>
              </div>

              <button
                className="btn-primary-action"
                onClick={() => onSelectSession(latestSession.id)}
              >
                <span>Inspect Session Kinematics</span>
                <ArrowRight size={16} />
              </button>
            </div>
          ) : (
            <div className="spotlight-empty">
              <p>No sessions recorded yet. Start an exercise on the phone to populate real rehabilitation data.</p>
            </div>
          )}
        </div>

        {/* Longitudinal Session Trend */}
        <div className="glass-card trend-card">
          <div className="section-card-header">
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <TrendingUp size={18} color="#10B981" />
              <h3>Longitudinal Trend</h3>
            </div>
            <span className="sample-size-pill">
              {sessions.length} {sessions.length === 1 ? 'Session' : 'Sessions'}
            </span>
          </div>

          {!hasMultipleSessions ? (
            <div className="trend-insufficient-data">
              <AlertCircle size={32} color="#F59E0B" />
              <h4>Not enough sessions for a trend.</h4>
              <p>
                {sessions.length === 0
                  ? 'No sessions recorded yet.'
                  : 'One session recorded. More sessions are needed for longitudinal trends.'}
              </p>
              <span className="trend-notice">
                KinexMed never fabricates simulated progression. Longitudinal trend analysis unlocks when at least 2 sessions are recorded.
              </span>
            </div>
          ) : (
            <div className="trend-content">
              <div className="trend-highlight-box">
                <div className="trend-delta-row">
                  <span className="trend-delta-label">Flexion Depth Evolution:</span>
                  <span className={`trend-delta-value ${romDelta <= 0 ? 'good' : 'warning'}`}>
                    {romDelta > 0 ? `+${romDelta}°` : `${romDelta}°`}
                  </span>
                </div>
                <p className="trend-delta-desc">
                  {romDelta < 0
                    ? `Patient is reaching ${Math.abs(romDelta)}° deeper flexion compared to initial baseline.`
                    : romDelta === 0
                    ? 'Patient flexion depth remains consistent with baseline.'
                    : `Average flexion is ${romDelta}° shallower than baseline.`}
                </p>
              </div>

              <div className="trend-highlight-box" style={{ marginTop: '12px' }}>
                <div className="trend-delta-row">
                  <span className="trend-delta-label">Valid Rep Adherence:</span>
                  <span className={`trend-delta-value ${adherenceDelta >= 0 ? 'good' : 'warning'}`}>
                    {adherenceDelta >= 0 ? `+${adherenceDelta}%` : `${adherenceDelta}%`}
                  </span>
                </div>
                <p className="trend-delta-desc">
                  {adherenceDelta >= 0
                    ? `Clinical adherence increased by ${adherenceDelta}% across ${sessions.length} sessions.`
                    : `Clinical adherence decreased by ${Math.abs(adherenceDelta)}%. Check rep failure reasons.`}
                </p>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 3. Recent Exercise Activity List */}
      <div className="glass-card activity-card" style={{ marginTop: '1.5rem' }}>
        <div className="section-card-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Clock size={18} color="#38BDF8" />
            <h3>Recent Exercise Activity</h3>
          </div>
          {sessions.length > 0 && (
            <button className="btn-text-action" onClick={onViewAllSessions}>
              View All ({sessions.length}) →
            </button>
          )}
        </div>

        {sessions.length === 0 ? (
          <div style={{ padding: '2rem', textAlign: 'center', color: '#64748B' }}>
            No recent activity logged.
          </div>
        ) : (
          <div className="recent-activity-list">
            {sessions.slice(0, 5).map((session, idx) => (
              <div
                key={session.id}
                className="activity-item"
                onClick={() => onSelectSession(session.id)}
              >
                <div className="activity-index">#{sessions.length - idx}</div>
                <div className="activity-info">
                  <span className="activity-title">
                    {session.exercise_name.toUpperCase()} · {session.valid_reps}/{session.total_reps} Valid Reps
                  </span>
                  <span className="activity-time">
                    {new Date(session.created_at || session.started_at).toLocaleString(undefined, {
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit'
                    })} · Duration: {Math.round(session.duration_seconds)}s
                  </span>
                </div>
                <div className="activity-stat">
                  <span className="stat-angle">{Math.round(session.avg_peak_knee_angle)}° ROM</span>
                  <span className="stat-adherence">
                    {session.total_reps > 0
                      ? `${Math.round((session.valid_reps / session.total_reps) * 100)}% valid`
                      : '0 reps'}
                  </span>
                </div>
                <ArrowRight size={16} color="#64748B" />
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

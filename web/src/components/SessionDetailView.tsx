import React, { useState, useEffect } from 'react';
import type { SessionDetail, SessionSummary, SessionNlSummary } from '../types/session';
import { RomChart } from './RomChart';
import { fetchSessionSummary } from '../api/client';
import { Check, X, Clock, Target, Sparkles } from 'lucide-react';

interface SessionDetailViewProps {
  session: SessionDetail;
  allSessions: SessionSummary[];
  onInspectReps?: () => void;
}

export const SessionDetailView: React.FC<SessionDetailViewProps> = ({
  session,
  allSessions,
  onInspectReps
}) => {
  const [viewMode, setViewMode] = useState<'SESSION_ROM' | 'ROM_TREND'>('SESSION_ROM');
  const [nlSummary, setNlSummary] = useState<SessionNlSummary | null>(null);
  const [loadingSummary, setLoadingSummary] = useState<boolean>(false);

  useEffect(() => {
    let isMounted = true;
    async function loadSummary() {
      setLoadingSummary(true);
      try {
        const data = await fetchSessionSummary(session.id);
        if (isMounted) setNlSummary(data);
      } catch (err) {
        console.error('Error fetching session summary:', err);
      } finally {
        if (isMounted) setLoadingSummary(false);
      }
    }
    loadSummary();
    return () => {
      isMounted = false;
    };
  }, [session.id]);

  const formattedDate = new Date(session.created_at || session.started_at).toLocaleString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });

  const adherence = session.total_reps > 0
    ? Math.round((session.valid_reps / session.total_reps) * 100)
    : 0;

  return (
    <div className="glass-card detail-panel">
      {/* Detail Header */}
      <div className="detail-header">
        <div className="detail-title-group">
          <h2>Session: {session.exercise_name.toUpperCase()}</h2>
          <p style={{ color: '#94A3B8', fontSize: '0.85rem' }}>
            Recorded on {formattedDate} · Device: <strong>{session.device_id}</strong>
          </p>
        </div>

        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
          <div className="status-indicator">
            <Clock size={14} color="#38BDF8" />
            <span>Duration: {Math.round(session.duration_seconds)}s</span>
          </div>
          <div className="status-indicator">
            <Target size={14} color="#10B981" />
            <span>
              {session.valid_reps} / {session.total_reps} Valid ({adherence}%)
            </span>
          </div>
        </div>
      </div>

      {/* Metrics Row */}
      <div className="detail-metrics-row">
        <div className="detail-metric-card">
          <span className="dm-label">Avg Peak ROM</span>
          <span className="dm-value" style={{ color: '#06B6D4' }}>
            {Math.round(session.avg_peak_knee_angle)}°
          </span>
          <span className="dm-sub">Knee flexion depth</span>
        </div>

        <div className="detail-metric-card">
          <span className="dm-label">Deepest Flexion</span>
          <span className="dm-value" style={{ color: '#10B981' }}>
            {session.min_knee_angle > 0 ? `${Math.round(session.min_knee_angle)}°` : '--'}
          </span>
          <span className="dm-sub">Max joint excursion</span>
        </div>

        <div className="detail-metric-card">
          <span className="dm-label">Extension Return</span>
          <span className="dm-value" style={{ color: '#38BDF8' }}>
            {session.max_knee_angle > 0 ? `${Math.round(session.max_knee_angle)}°` : '--'}
          </span>
          <span className="dm-sub">Upright stance (≥145°)</span>
        </div>

        <div className="detail-metric-card">
          <span className="dm-label">Evidence Flags</span>
          <span
            className="dm-value"
            style={{ color: session.evidence_failure_count > 0 ? '#F59E0B' : '#64748B' }}
          >
            {session.evidence_failure_count}
          </span>
          <span className="dm-sub">Positioning alerts</span>
        </div>
      </div>

      {/* Natural Language Clinician Summary Box */}
      {loadingSummary && !nlSummary && (
        <div className="nl-summary-box" style={{ opacity: 0.7 }}>
          <span style={{ fontSize: '0.85rem', color: '#94A3B8' }}>Generating clinical kinematic summary...</span>
        </div>
      )}

      {nlSummary && (
        <div className="nl-summary-box">
          <div className="nl-summary-header">
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Sparkles size={16} color="#06B6D4" />
              <strong>Clinician Kinematic Summary</strong>
            </div>
            <span className="source-tag">{nlSummary.source.replace('_', ' ').toUpperCase()}</span>
          </div>
          <p className="nl-summary-text">{nlSummary.summary}</p>
          <span className="nl-disclaimer">{nlSummary.disclaimer}</span>
        </div>
      )}

      {/* ROM Kinematics Chart */}
      <div style={{ marginTop: '1.5rem' }}>
        <RomChart
          currentSession={session}
          allSessions={allSessions}
          viewMode={viewMode}
          onToggleViewMode={setViewMode}
        />
      </div>

      {/* Repetition Breakdown Table */}
      <div style={{ marginTop: '2rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h4 style={{ fontSize: '1rem', color: '#F8FAFC' }}>
            REPETITION KINEMATIC BREAKDOWN ({session.reps.length})
          </h4>
          {onInspectReps && (
            <button className="btn-text-action" onClick={onInspectReps}>
              Deep Rep Analysis →
            </button>
          )}
        </div>

        <div className="reps-table-wrapper">
          <table className="reps-table">
            <thead>
              <tr>
                <th>Rep #</th>
                <th>Validity</th>
                <th>Peak Knee ROM</th>
                <th>Extension Arc</th>
                <th>Duration</th>
                <th>Clinical / Form Feedback</th>
              </tr>
            </thead>
            <tbody>
              {session.reps.map((rep) => {
                const durationSec = (rep.duration_ms / 1000).toFixed(1);
                return (
                  <tr key={rep.id}>
                    <td style={{ fontWeight: 700, color: '#F8FAFC' }}>#{rep.rep_number}</td>
                    <td>
                      <span className={`rep-valid-pill ${rep.is_valid ? 'valid' : 'invalid'}`}>
                        {rep.is_valid ? (
                          <>
                            <Check size={12} /> Valid
                          </>
                        ) : (
                          <>
                            <X size={12} /> Incomplete
                          </>
                        )}
                      </span>
                    </td>
                    <td style={{ fontWeight: 700, color: '#38BDF8' }}>
                      {Math.round(rep.peak_knee_angle)}°
                    </td>
                    <td>
                      {Math.round(rep.start_knee_angle)}° → {Math.round(rep.end_knee_angle)}°
                    </td>
                    <td>{durationSec}s</td>
                    <td>
                      <div>
                        <span style={{ color: rep.is_valid ? '#E2E8F0' : '#FCA5A5' }}>
                          {rep.feedback_message || 'Completed'}
                        </span>
                        {rep.failure_reasons && rep.failure_reasons.length > 0 && (
                          <div style={{ fontSize: '0.75rem', color: '#F87171', marginTop: '2px' }}>
                            {rep.failure_reasons.join(', ')}
                          </div>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

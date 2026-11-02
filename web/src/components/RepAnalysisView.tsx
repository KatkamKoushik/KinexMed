import React, { useState } from 'react';
import type { SessionDetail } from '../types/session';
import { Check, X, AlertTriangle, Activity, ArrowLeft } from 'lucide-react';

interface RepAnalysisViewProps {
  session: SessionDetail | null;
  onBackToSessions?: () => void;
}

export const RepAnalysisView: React.FC<RepAnalysisViewProps> = ({
  session,
  onBackToSessions
}) => {
  const [filter, setFilter] = useState<'ALL' | 'VALID' | 'INVALID'>('ALL');

  if (!session) {
    return (
      <div className="glass-card" style={{ padding: '3rem', textAlign: 'center', color: '#94A3B8' }}>
        <h3>No Session Selected</h3>
        <p>Please select a session from the Session List to perform rep-by-rep kinematic analysis.</p>
        {onBackToSessions && (
          <button className="btn-primary-action" style={{ maxWidth: '200px', margin: '1rem auto' }} onClick={onBackToSessions}>
            Go to Session List
          </button>
        )}
      </div>
    );
  }

  const reps = session.reps || [];
  const filteredReps = reps.filter((r) => {
    if (filter === 'VALID') return r.is_valid;
    if (filter === 'INVALID') return !r.is_valid;
    return true;
  });

  const validCount = reps.filter((r) => r.is_valid).length;
  const invalidCount = reps.filter((r) => !r.is_valid).length;
  const avgRepDuration = reps.length > 0
    ? (reps.reduce((acc, r) => acc + r.duration_ms, 0) / reps.length / 1000).toFixed(1)
    : '0';

  const bestFlexion = reps.length > 0
    ? Math.min(...reps.map((r) => r.peak_knee_angle))
    : null;

  return (
    <div className="rep-analysis-page">
      {/* Header with Session Context */}
      <div className="rep-analysis-header">
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '4px' }}>
            <Activity size={20} color="#06B6D4" />
            <h2>Repetition Kinematic Inspection</h2>
            {onBackToSessions && (
              <button
                className="btn-inspect-mini"
                style={{ marginLeft: '12px' }}
                onClick={onBackToSessions}
              >
                <ArrowLeft size={12} /> Back
              </button>
            )}
          </div>
          <p style={{ color: '#94A3B8', fontSize: '0.85rem' }}>
            Session ID: <code style={{ color: '#38BDF8' }}>{session.id.slice(0, 8)}...</code> · Exercise:{' '}
            <strong>{session.exercise_name.toUpperCase()}</strong> · {reps.length} Reps Total
          </p>
        </div>

        {/* Rep Summary Badges */}
        <div className="rep-summary-pills">
          <div className="stat-pill">
            <span className="pill-label">Valid Reps</span>
            <span className="pill-val" style={{ color: '#10B981' }}>{validCount}</span>
          </div>
          <div className="stat-pill">
            <span className="pill-label">Rejected Reps</span>
            <span className="pill-val" style={{ color: '#F59E0B' }}>{invalidCount}</span>
          </div>
          <div className="stat-pill">
            <span className="pill-label">Avg Rep Duration</span>
            <span className="pill-val">{avgRepDuration}s</span>
          </div>
          <div className="stat-pill">
            <span className="pill-label">Max Depth</span>
            <span className="pill-val" style={{ color: '#06B6D4' }}>
              {bestFlexion !== null ? `${Math.round(bestFlexion)}°` : '--'}
            </span>
          </div>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="filter-pill-group" style={{ marginBottom: '1rem' }}>
        <button
          className={`filter-pill ${filter === 'ALL' ? 'active' : ''}`}
          onClick={() => setFilter('ALL')}
        >
          All Repetitions ({reps.length})
        </button>
        <button
          className={`filter-pill ${filter === 'VALID' ? 'active' : ''}`}
          onClick={() => setFilter('VALID')}
        >
          Valid Only ({validCount})
        </button>
        <button
          className={`filter-pill ${filter === 'INVALID' ? 'active' : ''}`}
          onClick={() => setFilter('INVALID')}
        >
          Rejected Only ({invalidCount})
        </button>
      </div>

      {/* Rep Cards Grid / Table */}
      {filteredReps.length === 0 ? (
        <div className="glass-card" style={{ padding: '2.5rem', textAlign: 'center', color: '#64748B' }}>
          No repetitions match the selected filter.
        </div>
      ) : (
        <div className="reps-grid">
          {filteredReps.map((rep) => {
            const durationSec = (rep.duration_ms / 1000).toFixed(1);
            const isDepthMet = rep.peak_knee_angle <= 100.0;

            return (
              <div
                key={rep.id || rep.rep_number}
                className={`glass-card rep-card ${rep.is_valid ? 'rep-card-valid' : 'rep-card-invalid'}`}
              >
                <div className="rep-card-top">
                  <div className="rep-number-badge">REP #{rep.rep_number}</div>
                  <span className={`rep-valid-pill ${rep.is_valid ? 'valid' : 'invalid'}`}>
                    {rep.is_valid ? (
                      <>
                        <Check size={12} /> Valid Rep
                      </>
                    ) : (
                      <>
                        <X size={12} /> Rejected
                      </>
                    )}
                  </span>
                </div>

                <div className="rep-card-metrics">
                  <div className="metric-col">
                    <span className="metric-col-title">Peak Flexion</span>
                    <span className="metric-col-val" style={{ color: isDepthMet ? '#10B981' : '#F59E0B' }}>
                      {Math.round(rep.peak_knee_angle)}°
                    </span>
                    <span className="metric-col-sub">{isDepthMet ? 'Target Met (≤100°)' : 'Too Shallow'}</span>
                  </div>

                  <div className="metric-col">
                    <span className="metric-col-title">Extension Arc</span>
                    <span className="metric-col-val" style={{ color: '#E2E8F0' }}>
                      {Math.round(rep.start_knee_angle)}° → {Math.round(rep.end_knee_angle)}°
                    </span>
                    <span className="metric-col-sub">Start to Finish</span>
                  </div>

                  <div className="metric-col">
                    <span className="metric-col-title">Duration</span>
                    <span className="metric-col-val" style={{ color: '#38BDF8' }}>
                      {durationSec}s
                    </span>
                    <span className="metric-col-sub">Elapsed time</span>
                  </div>
                </div>

                <div className="rep-feedback-box">
                  <span className="feedback-label">Clinical Feedback:</span>
                  <p className="feedback-text">{rep.feedback_message || 'Standard movement trajectory.'}</p>

                  {rep.failure_reasons && rep.failure_reasons.length > 0 && (
                    <div className="reasons-list">
                      {rep.failure_reasons.map((reason, rIdx) => (
                        <div key={rIdx} className="reason-item">
                          <AlertTriangle size={12} color="#F87171" />
                          <span>{reason}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

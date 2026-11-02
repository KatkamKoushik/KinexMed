import React, { useState } from 'react';
import type { SessionSummary } from '../types/session';
import { Search, ArrowRight, ShieldAlert, CheckCircle2 } from 'lucide-react';

interface SessionListViewProps {
  sessions: SessionSummary[];
  selectedId: string | null;
  onSelectSession: (id: string) => void;
}

export const SessionListView: React.FC<SessionListViewProps> = ({
  sessions,
  selectedId,
  onSelectSession
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [filterMode, setFilterMode] = useState<'ALL' | 'PERFECT' | 'WARNINGS'>('ALL');
  const [exerciseFilter, setExerciseFilter] = useState<string>('ALL');

  const filteredSessions = sessions.filter((s) => {
    const matchesExercise =
      exerciseFilter === 'ALL' || s.exercise_name.toLowerCase() === exerciseFilter.toLowerCase();
    if (!matchesExercise) return false;

    const matchesSearch =
      s.exercise_name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      s.device_id.toLowerCase().includes(searchTerm.toLowerCase()) ||
      s.id.toLowerCase().includes(searchTerm.toLowerCase());

    if (!matchesSearch) return false;

    if (filterMode === 'PERFECT') {
      return s.valid_reps === s.total_reps && s.total_reps > 0;
    }
    if (filterMode === 'WARNINGS') {
      return s.evidence_failure_count > 0;
    }
    return true;
  });

  return (
    <div className="session-list-page">
      <div className="session-list-header">
        <div>
          <h2>Recorded Rehabilitation Sessions</h2>
          <p style={{ color: '#94A3B8', fontSize: '0.85rem' }}>
            Telemetry ingested from patient Android devices. Total: {sessions.length} sessions
          </p>
        </div>

        {/* Filter / Search Controls */}
        <div className="filter-controls-row">
          <div className="search-box">
            <Search size={16} color="#64748B" />
            <input
              type="text"
              placeholder="Search by exercise or device..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>

          <div className="filter-pill-group">
            <button
              className={`filter-pill ${exerciseFilter === 'ALL' ? 'active' : ''}`}
              onClick={() => setExerciseFilter('ALL')}
            >
              All Exercises
            </button>
            <button
              className={`filter-pill ${exerciseFilter === 'squat' ? 'active' : ''}`}
              onClick={() => setExerciseFilter('squat')}
            >
              Squat
            </button>
            <button
              className={`filter-pill ${exerciseFilter === 'sit_to_stand' ? 'active' : ''}`}
              onClick={() => setExerciseFilter('sit_to_stand')}
            >
              Sit-to-Stand
            </button>
            <button
              className={`filter-pill ${exerciseFilter === 'lunge' ? 'active' : ''}`}
              onClick={() => setExerciseFilter('lunge')}
            >
              Lunge
            </button>
            <button
              className={`filter-pill ${exerciseFilter === 'calf_raise' ? 'active' : ''}`}
              onClick={() => setExerciseFilter('calf_raise')}
            >
              Calf Raise
            </button>
          </div>

          <div className="filter-pill-group">
            <button
              className={`filter-pill ${filterMode === 'ALL' ? 'active' : ''}`}
              onClick={() => setFilterMode('ALL')}
            >
              All Status
            </button>
            <button
              className={`filter-pill ${filterMode === 'PERFECT' ? 'active' : ''}`}
              onClick={() => setFilterMode('PERFECT')}
            >
              100% Adherence
            </button>
            <button
              className={`filter-pill ${filterMode === 'WARNINGS' ? 'active' : ''}`}
              onClick={() => setFilterMode('WARNINGS')}
            >
              With Warnings
            </button>
          </div>
        </div>
      </div>

      {filteredSessions.length === 0 ? (
        <div className="glass-card" style={{ padding: '3rem', textAlign: 'center', color: '#64748B' }}>
          {sessions.length === 0
            ? 'No sessions recorded yet.'
            : 'No sessions matched your search criteria.'}
        </div>
      ) : (
        <div className="session-table-wrapper glass-card">
          <table className="clinician-table">
            <thead>
              <tr>
                <th>Date & Time</th>
                <th>Exercise</th>
                <th>Device ID</th>
                <th>Duration</th>
                <th>Repetitions</th>
                <th>Adherence</th>
                <th>Peak Flexion</th>
                <th>Evidence Flags</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {filteredSessions.map((s) => {
                const isSelected = s.id === selectedId;
                const adherence = s.total_reps > 0 ? Math.round((s.valid_reps / s.total_reps) * 100) : 0;
                const dateStr = new Date(s.created_at || s.started_at).toLocaleString(undefined, {
                  month: 'short',
                  day: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit'
                });

                return (
                  <tr
                    key={s.id}
                    className={`table-row-clickable ${isSelected ? 'row-selected' : ''}`}
                    onClick={() => onSelectSession(s.id)}
                  >
                    <td style={{ fontWeight: 600, color: '#F8FAFC' }}>{dateStr}</td>
                    <td>
                      <span className="exercise-badge">{s.exercise_name.toUpperCase()}</span>
                    </td>
                    <td style={{ color: '#94A3B8' }}>{s.device_id}</td>
                    <td>{Math.round(s.duration_seconds)}s</td>
                    <td style={{ fontWeight: 600 }}>
                      <span style={{ color: '#10B981' }}>{s.valid_reps}</span>
                      <span style={{ color: '#64748B' }}> / {s.total_reps}</span>
                    </td>
                    <td>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <div className="progress-track">
                          <div
                            className="progress-fill"
                            style={{
                              width: `${adherence}%`,
                              backgroundColor: adherence >= 80 ? '#10B981' : adherence >= 50 ? '#F59E0B' : '#EF4444'
                            }}
                          ></div>
                        </div>
                        <span style={{ fontSize: '0.75rem', fontWeight: 600 }}>{adherence}%</span>
                      </div>
                    </td>
                    <td style={{ fontWeight: 700, color: '#06B6D4' }}>
                      {Math.round(s.avg_peak_knee_angle)}°
                    </td>
                    <td>
                      {s.evidence_failure_count > 0 ? (
                        <span className="warning-count-pill">
                          <ShieldAlert size={12} /> {s.evidence_failure_count}
                        </span>
                      ) : (
                        <span className="clean-count-pill">
                          <CheckCircle2 size={12} /> Clear
                        </span>
                      )}
                    </td>
                    <td>
                      <button className="btn-inspect-mini" onClick={() => onSelectSession(s.id)}>
                        <span>Inspect</span>
                        <ArrowRight size={14} />
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

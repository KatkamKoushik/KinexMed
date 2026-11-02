import React from 'react';
import type { SessionSummary } from '../types/session';
import { Award, Layers, ShieldCheck, Timer } from 'lucide-react';

interface KpiCardsProps {
  sessions: SessionSummary[];
}

export const KpiCards: React.FC<KpiCardsProps> = ({ sessions }) => {
  const totalSessions = sessions.length;
  const totalValidReps = sessions.reduce((acc, s) => acc + s.valid_reps, 0);
  const totalReps = sessions.reduce((acc, s) => acc + s.total_reps, 0);

  const avgPeakRom = totalSessions > 0
    ? Math.round(sessions.reduce((acc, s) => acc + s.avg_peak_knee_angle, 0) / totalSessions)
    : null;

  const totalEvidenceWarnings = sessions.reduce((acc, s) => acc + s.evidence_failure_count, 0);

  return (
    <div className="kpi-grid">
      <div className="glass-card kpi-card">
        <div className="kpi-header">
          <span>Recorded Sessions</span>
          <Layers size={18} color="#06B6D4" />
        </div>
        <div className="kpi-value">{totalSessions}</div>
        <div className="kpi-sub">
          {totalSessions > 0 ? 'Verified on-device phone sessions' : 'Awaiting first session'}
        </div>
      </div>

      <div className="glass-card kpi-card">
        <div className="kpi-header">
          <span>Valid Repetitions</span>
          <Award size={18} color="#10B981" />
        </div>
        <div className="kpi-value" style={{ color: '#10B981' }}>{totalValidReps}</div>
        <div className="kpi-sub">
          {totalReps > 0 ? `Out of ${totalReps} total attempts (${Math.round((totalValidReps / totalReps) * 100)}%)` : 'Deterministic rule validation'}
        </div>
      </div>

      <div className="glass-card kpi-card">
        <div className="kpi-header">
          <span>Average Peak ROM</span>
          <Timer size={18} color="#3B82F6" />
        </div>
        <div className="kpi-value" style={{ color: '#38BDF8' }}>
          {avgPeakRom !== null ? `${avgPeakRom}°` : '--°'}
        </div>
        <div className="kpi-sub">
          {avgPeakRom !== null ? 'Knee flexion depth achieved' : 'Measured from landmark vectors'}
        </div>
      </div>

      <div className="glass-card kpi-card">
        <div className="kpi-header">
          <span>Evidence Gating Flags</span>
          <ShieldCheck size={18} color="#F59E0B" />
        </div>
        <div className="kpi-value" style={{ color: totalEvidenceWarnings > 0 ? '#F59E0B' : '#94A3B8' }}>
          {totalEvidenceWarnings}
        </div>
        <div className="kpi-sub">
          {totalEvidenceWarnings > 0 ? 'Repositioning alerts issued' : 'Zero tracking corruption'}
        </div>
      </div>
    </div>
  );
};

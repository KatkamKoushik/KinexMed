import React from 'react';
import type { SessionSummary } from '../types/session';
import { ShieldCheck, CheckCircle2, ShieldAlert, Cpu, Lock } from 'lucide-react';

interface EvidenceQualityViewProps {
  sessions: SessionSummary[];
}

export const EvidenceQualityView: React.FC<EvidenceQualityViewProps> = ({ sessions }) => {
  const totalSessions = sessions.length;
  const totalWarnings = sessions.reduce((acc, s) => acc + s.evidence_failure_count, 0);
  const cleanSessions = sessions.filter((s) => s.evidence_failure_count === 0).length;
  const reliabilityRate = totalSessions > 0
    ? Math.round((cleanSessions / totalSessions) * 100)
    : 100;

  return (
    <div className="evidence-quality-page">
      <div className="page-header">
        <div>
          <h2>Visual Evidence &amp; Camera Quality Telemetry</h2>
          <p style={{ color: '#94A3B8', fontSize: '0.85rem' }}>
            Verification of anatomical landmark confidence, framing geometry, and edge compute privacy.
          </p>
        </div>
      </div>

      {/* Quality KPI Cards */}
      <div className="quality-kpi-grid">
        <div className="glass-card quality-card">
          <div className="quality-card-icon" style={{ backgroundColor: 'rgba(16, 185, 129, 0.15)' }}>
            <ShieldCheck size={24} color="#10B981" />
          </div>
          <div className="quality-card-body">
            <span className="quality-val" style={{ color: '#10B981' }}>{reliabilityRate}%</span>
            <span className="quality-label">Framing Reliability</span>
            <span className="quality-sub">{cleanSessions} of {totalSessions} sessions had 0 warnings</span>
          </div>
        </div>

        <div className="glass-card quality-card">
          <div className="quality-card-icon" style={{ backgroundColor: 'rgba(245, 158, 11, 0.15)' }}>
            <ShieldAlert size={24} color="#F59E0B" />
          </div>
          <div className="quality-card-body">
            <span className="quality-val" style={{ color: totalWarnings > 0 ? '#F59E0B' : '#64748B' }}>
              {totalWarnings}
            </span>
            <span className="quality-label">Total Evidence Flags</span>
            <span className="quality-sub">Prompted patient to reposition</span>
          </div>
        </div>

        <div className="glass-card quality-card">
          <div className="quality-card-icon" style={{ backgroundColor: 'rgba(6, 182, 212, 0.15)' }}>
            <Cpu size={24} color="#06B6D4" />
          </div>
          <div className="quality-card-body">
            <span className="quality-val" style={{ color: '#06B6D4' }}>100%</span>
            <span className="quality-label">On-Device Processing</span>
            <span className="quality-sub">Local MediaPipe Pose Landmarker</span>
          </div>
        </div>
      </div>

      {/* Edge Compute Privacy Statement */}
      <div className="glass-card privacy-card" style={{ marginTop: '1.5rem' }}>
        <div className="privacy-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Lock size={20} color="#06B6D4" />
            <h3>Zero Video Streaming &amp; Patient Privacy Architecture</h3>
          </div>
          <span className="compliance-pill">HIPAA &amp; GDPR Compliant by Design</span>
        </div>

        <div className="privacy-grid">
          <div className="privacy-item">
            <CheckCircle2 size={16} color="#10B981" />
            <div>
              <strong>No Cloud Video Streams:</strong>
              <p>Camera frames are processed entirely in phone memory (GPU/NPU) and discarded immediately after landmark vector extraction.</p>
            </div>
          </div>

          <div className="privacy-item">
            <CheckCircle2 size={16} color="#10B981" />
            <div>
              <strong>Deterministic Geometry:</strong>
              <p>Joint angles (hip-knee-ankle) are calculated using euclidean dot products on-device, never by a probabilistic remote vision model.</p>
            </div>
          </div>

          <div className="privacy-item">
            <CheckCircle2 size={16} color="#10B981" />
            <div>
              <strong>Telemetry Storage:</strong>
              <p>The backend stores only structured scalar numbers: session duration, joint angle values, timestamps, and repetition validity counts.</p>
            </div>
          </div>

          <div className="privacy-item">
            <CheckCircle2 size={16} color="#10B981" />
            <div>
              <strong>Non-Diagnostic Biofeedback:</strong>
              <p>KinexMed provides visual and auditory movement guidance. It does not output medical diagnoses or independently prescribe treatments.</p>
            </div>
          </div>
        </div>
      </div>

      {/* Per-Session Evidence Log */}
      <div className="glass-card" style={{ marginTop: '1.5rem', padding: '1.5rem' }}>
        <h3 style={{ fontSize: '1.1rem', marginBottom: '1rem', color: '#F8FAFC' }}>
          Per-Session Visual Evidence Log
        </h3>

        {sessions.length === 0 ? (
          <div style={{ padding: '2rem', textAlign: 'center', color: '#64748B' }}>
            No sessions recorded yet.
          </div>
        ) : (
          <table className="clinician-table">
            <thead>
              <tr>
                <th>Session ID</th>
                <th>Date &amp; Time</th>
                <th>Device</th>
                <th>Evidence Warnings</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map((s) => (
                <tr key={s.id}>
                  <td><code>{s.id.slice(0, 10)}...</code></td>
                  <td>
                    {new Date(s.created_at || s.started_at).toLocaleString(undefined, {
                      month: 'short',
                      day: 'numeric',
                      hour: '2-digit',
                      minute: '2-digit'
                    })}
                  </td>
                  <td>{s.device_id}</td>
                  <td>
                    {s.evidence_failure_count > 0 ? (
                      <span style={{ color: '#F59E0B', fontWeight: 600 }}>
                        {s.evidence_failure_count} positioning alert(s)
                      </span>
                    ) : (
                      <span style={{ color: '#10B981', fontWeight: 600 }}>0 warnings (Clean)</span>
                    )}
                  </td>
                  <td>
                    <span className={`rep-valid-pill ${s.evidence_failure_count === 0 ? 'valid' : 'invalid'}`}>
                      {s.evidence_failure_count === 0 ? 'Optimal Framing' : 'Repositioning Required'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
};

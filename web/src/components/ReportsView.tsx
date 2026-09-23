import React, { useState, useEffect } from 'react';
import type { WeeklyReport, MonthlyReport } from '../types/session';
import { fetchWeeklyReport, fetchMonthlyReport } from '../api/client';
import { FileText, Copy, Check, Calendar, Award, AlertTriangle, ShieldCheck, Activity } from 'lucide-react';

export const ReportsView: React.FC = () => {
  const [reportType, setReportType] = useState<'WEEKLY' | 'MONTHLY'>('WEEKLY');
  const [weeklyReport, setWeeklyReport] = useState<WeeklyReport | null>(null);
  const [monthlyReport, setMonthlyReport] = useState<MonthlyReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [copied, setCopied] = useState(false);
  const [targetSessions, setTargetSessions] = useState(5);

  const loadReports = async () => {
    try {
      setLoading(true);
      const [weekly, monthly] = await Promise.all([
        fetchWeeklyReport(targetSessions),
        fetchMonthlyReport()
      ]);
      setWeeklyReport(weekly);
      setMonthlyReport(monthly);
    } catch (err) {
      console.error('Failed to fetch reports:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReports();
  }, [targetSessions]);

  const generateReportText = (): string => {
    const timestamp = new Date().toLocaleString();
    if (reportType === 'WEEKLY' && weeklyReport) {
      const dist = Object.entries(weeklyReport.exercise_distribution)
        .map(([k, v]) => `  - ${k.replace(/_/g, ' ').toUpperCase()}: ${v} valid reps`)
        .join('\n') || '  - None recorded';

      return `KINEXMED WEEKLY CLINICAL ADHERENCE REPORT
Generated: ${timestamp}
Week Starting: ${new Date(weeklyReport.start_date).toLocaleDateString()}
Completed Sessions: ${weeklyReport.completed_sessions} / ${weeklyReport.target_sessions} target
Prescription Goal: ${weeklyReport.goal_met ? 'MET' : 'INCOMPLETE'}
Valid Repetitions: ${weeklyReport.total_valid_reps} / ${weeklyReport.total_attempted_reps} (${weeklyReport.adherence_ratio_percent}%)
Total Active Duration: ${Math.round(weeklyReport.total_duration_seconds / 60)} minutes

EXERCISE BREAKDOWN:
${dist}

CLINICAL GUARDRAIL:
Assistive kinematic observation only; non-diagnostic. Does not replace professional clinical evaluation.`;
    }

    if (reportType === 'MONTHLY' && monthlyReport) {
      const dist = Object.entries(monthlyReport.exercise_distribution)
        .map(([k, v]) => `  - ${k.replace(/_/g, ' ').toUpperCase()}: ${v} valid reps`)
        .join('\n') || '  - None recorded';

      return `KINEXMED MONTHLY CLINICAL REHABILITATION SUMMARY
Generated: ${timestamp}
Billing/Calendar Month: ${monthlyReport.month}
Total Sessions Completed: ${monthlyReport.completed_sessions}
Total Reps Validated: ${monthlyReport.total_valid_reps} / ${monthlyReport.total_attempted_reps} (${monthlyReport.adherence_ratio_percent}%)
Therapy Volume: ${monthlyReport.total_therapy_hours} hours

EXERCISE VOLUME:
${dist}

CLINICAL GUARDRAIL:
Assistive kinematic observation only; non-diagnostic. Does not replace professional clinical evaluation.`;
    }

    return '';
  };

  const handleCopyReport = () => {
    const text = generateReportText();
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="reports-container">
      <div className="reports-header">
        <div>
          <h2>Clinical Adherence Reports</h2>
          <p className="reports-subtitle">
            Exportable structured compliance telemetry, target goal tracking, and functional recovery summaries.
          </p>
        </div>

        <div className="reports-controls">
          <div className="view-toggle-pills">
            <button
              className={`toggle-pill ${reportType === 'WEEKLY' ? 'active' : ''}`}
              onClick={() => setReportType('WEEKLY')}
            >
              Weekly Adherence
            </button>
            <button
              className={`toggle-pill ${reportType === 'MONTHLY' ? 'active' : ''}`}
              onClick={() => setReportType('MONTHLY')}
            >
              Monthly Summary
            </button>
          </div>

          <button className="btn-primary-action" onClick={handleCopyReport}>
            {copied ? <Check size={16} /> : <Copy size={16} />}
            <span>{copied ? 'Copied' : 'Copy Text Report'}</span>
          </button>
        </div>
      </div>

      {loading ? (
        <div className="glass-card" style={{ padding: '3rem', textAlign: 'center', color: '#94A3B8' }}>
          Compiling clinical adherence analytics...
        </div>
      ) : (
        <>
          {reportType === 'WEEKLY' && weeklyReport && (
            <div className="report-content-flow">
              {/* Weekly KPI Cards */}
              <div className="kpi-grid">
                <div className="glass-card kpi-card">
                  <div className="kpi-icon-wrapper" style={{ background: 'rgba(6, 182, 212, 0.1)' }}>
                    <Calendar size={20} color="#06B6D4" />
                  </div>
                  <div className="kpi-details">
                    <span className="kpi-title">Weekly Sessions</span>
                    <span className="kpi-value">
                      {weeklyReport.completed_sessions} <span style={{ fontSize: '1rem', color: '#64748B' }}>/ {weeklyReport.target_sessions}</span>
                    </span>
                    <span className={`kpi-trend ${weeklyReport.goal_met ? 'positive' : 'neutral'}`}>
                      {weeklyReport.goal_met ? 'Prescription Goal Met' : `${weeklyReport.target_sessions - weeklyReport.completed_sessions} sessions remaining`}
                    </span>
                  </div>
                </div>

                <div className="glass-card kpi-card">
                  <div className="kpi-icon-wrapper" style={{ background: 'rgba(16, 185, 129, 0.1)' }}>
                    <Award size={20} color="#10B981" />
                  </div>
                  <div className="kpi-details">
                    <span className="kpi-title">Adherence Rate</span>
                    <span className="kpi-value">{weeklyReport.adherence_ratio_percent}%</span>
                    <span className="kpi-trend positive">
                      {weeklyReport.total_valid_reps} valid of {weeklyReport.total_attempted_reps} total
                    </span>
                  </div>
                </div>

                <div className="glass-card kpi-card">
                  <div className="kpi-icon-wrapper" style={{ background: 'rgba(56, 189, 248, 0.1)' }}>
                    <Activity size={20} color="#38BDF8" />
                  </div>
                  <div className="kpi-details">
                    <span className="kpi-title">Active Therapy Time</span>
                    <span className="kpi-value">
                      {Math.round(weeklyReport.total_duration_seconds / 60)} <span style={{ fontSize: '1rem', color: '#64748B' }}>min</span>
                    </span>
                    <span className="kpi-trend neutral">Monitored exercise duration</span>
                  </div>
                </div>
              </div>

              {/* Weekly Details Grid */}
              <div className="report-details-grid">
                <div className="glass-card report-section-card">
                  <div className="section-title">
                    <FileText size={18} color="#06B6D4" />
                    <h3>Exercise Distribution</h3>
                  </div>
                  {Object.keys(weeklyReport.exercise_distribution).length === 0 ? (
                    <div style={{ color: '#64748B', fontSize: '0.9rem', fontStyle: 'italic', padding: '1rem 0' }}>
                      No exercise repetitions recorded for the current week yet.
                    </div>
                  ) : (
                    <div className="dist-list">
                      {Object.entries(weeklyReport.exercise_distribution).map(([ex, count]) => (
                        <div key={ex} className="dist-item">
                          <span className="dist-name">{ex.replace(/_/g, ' ').toUpperCase()}</span>
                          <span className="dist-count">{count} valid reps</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div className="glass-card report-section-card">
                  <div className="section-title">
                    <ShieldCheck size={18} color="#10B981" />
                    <h3>Prescription Target Settings</h3>
                  </div>
                  <div className="target-setting-row">
                    <label>Target Weekly Sessions:</label>
                    <div className="target-input-group">
                      <input
                        type="number"
                        min={1}
                        max={14}
                        value={targetSessions}
                        onChange={(e) => setTargetSessions(parseInt(e.target.value) || 1)}
                      />
                      <span>sessions / week</span>
                    </div>
                  </div>
                  <p style={{ fontSize: '0.8rem', color: '#64748B', marginTop: '0.75rem' }}>
                    Adjusting this value re-evaluates the weekly adherence ratio against clinical recommendations.
                  </p>
                </div>
              </div>
            </div>
          )}

          {reportType === 'MONTHLY' && monthlyReport && (
            <div className="report-content-flow">
              {/* Monthly KPI Cards */}
              <div className="kpi-grid">
                <div className="glass-card kpi-card">
                  <div className="kpi-icon-wrapper" style={{ background: 'rgba(6, 182, 212, 0.1)' }}>
                    <Calendar size={20} color="#06B6D4" />
                  </div>
                  <div className="kpi-details">
                    <span className="kpi-title">Month</span>
                    <span className="kpi-value" style={{ fontSize: '1.4rem' }}>{monthlyReport.month}</span>
                    <span className="kpi-trend neutral">{monthlyReport.completed_sessions} completed sessions</span>
                  </div>
                </div>

                <div className="glass-card kpi-card">
                  <div className="kpi-icon-wrapper" style={{ background: 'rgba(16, 185, 129, 0.1)' }}>
                    <Award size={20} color="#10B981" />
                  </div>
                  <div className="kpi-details">
                    <span className="kpi-title">Monthly Adherence</span>
                    <span className="kpi-value">{monthlyReport.adherence_ratio_percent}%</span>
                    <span className="kpi-trend positive">
                      {monthlyReport.total_valid_reps} valid of {monthlyReport.total_attempted_reps} total
                    </span>
                  </div>
                </div>

                <div className="glass-card kpi-card">
                  <div className="kpi-icon-wrapper" style={{ background: 'rgba(56, 189, 248, 0.1)' }}>
                    <Activity size={20} color="#38BDF8" />
                  </div>
                  <div className="kpi-details">
                    <span className="kpi-title">Total Therapy Hours</span>
                    <span className="kpi-value">{monthlyReport.total_therapy_hours} <span style={{ fontSize: '1rem', color: '#64748B' }}>hrs</span></span>
                    <span className="kpi-trend neutral">Cumulative exercise volume</span>
                  </div>
                </div>
              </div>

              {/* Monthly Details Grid */}
              <div className="report-details-grid">
                <div className="glass-card report-section-card">
                  <div className="section-title">
                    <FileText size={18} color="#06B6D4" />
                    <h3>Monthly Exercise Breakdown</h3>
                  </div>
                  {Object.keys(monthlyReport.exercise_distribution).length === 0 ? (
                    <div style={{ color: '#64748B', fontSize: '0.9rem', fontStyle: 'italic', padding: '1rem 0' }}>
                      No exercise repetitions recorded for the current month yet.
                    </div>
                  ) : (
                    <div className="dist-list">
                      {Object.entries(monthlyReport.exercise_distribution).map(([ex, count]) => (
                        <div key={ex} className="dist-item">
                          <span className="dist-name">{ex.replace(/_/g, ' ').toUpperCase()}</span>
                          <span className="dist-count">{count} valid reps</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div className="glass-card report-section-card">
                  <div className="section-title">
                    <ShieldCheck size={18} color="#10B981" />
                    <h3>Rehabilitation Adherence Notes</h3>
                  </div>
                  <div style={{ fontSize: '0.85rem', color: '#94A3B8', lineHeight: 1.6 }}>
                    This monthly summary aggregates kinematic telemetry recorded on-device by patient smartphones. All angle computations, rep validations, and evidence checks are performed locally at the edge.
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Formatted Report Preview Box */}
          <div className="glass-card report-preview-card" style={{ marginTop: '1.5rem' }}>
            <div className="preview-header">
              <span className="preview-title">Formatted Export Preview</span>
              <button className="btn-text-action" onClick={handleCopyReport}>
                {copied ? 'Copied to Clipboard' : 'Copy All Text'}
              </button>
            </div>
            <pre className="report-text-pre">{generateReportText()}</pre>
          </div>

          {/* Clinical Guardrail Alert */}
          <div className="clinical-guardrail-banner">
            <AlertTriangle size={18} color="#F59E0B" />
            <span>
              <strong>Clinical Guardrail:</strong> Assistive kinematic observation only; non-diagnostic. Does not replace professional clinical evaluation or in-person physical therapy diagnosis.
            </span>
          </div>
        </>
      )}
    </div>
  );
};

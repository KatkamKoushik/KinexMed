import React from 'react';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
} from 'chart.js';
import { Line } from 'react-chartjs-2';
import type { SessionDetail, SessionSummary } from '../types/session';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

interface RomChartProps {
  currentSession: SessionDetail;
  allSessions: SessionSummary[];
  viewMode: 'SESSION_ROM' | 'ROM_TREND';
  onToggleViewMode: (mode: 'SESSION_ROM' | 'ROM_TREND') => void;
}

export const RomChart: React.FC<RomChartProps> = ({
  currentSession,
  allSessions,
  viewMode,
  onToggleViewMode
}) => {
  const hasMultipleSessions = allSessions.length > 1;

  const getExerciseMetadata = (exerciseName?: string) => {
    switch (exerciseName?.toLowerCase()) {
      case 'sit_to_stand':
        return {
          metricName: 'Extension Angle',
          targetLabel: 'Target Stand (≥155°)',
          targetVal: 155
        };
      case 'lunge':
        return {
          metricName: 'Lead Knee Angle',
          targetLabel: 'Target Lunge Depth (≤100°)',
          targetVal: 100
        };
      case 'calf_raise':
        return {
          metricName: 'Ankle Plantarflexion',
          targetLabel: 'Target Heel Rise (≥120°)',
          targetVal: 120
        };
      case 'squat':
      default:
        return {
          metricName: 'Knee Angle',
          targetLabel: 'Target Depth (≤100°)',
          targetVal: 100
        };
    }
  };

  const meta = getExerciseMetadata(currentSession?.exercise_name);

  // 1. Session ROM Data: per-rep peak joint angle in the active session
  const sessionLabels = currentSession.reps.map((r) => `Rep ${r.rep_number}`);
  const sessionDataPoints = currentSession.reps.map((r) => r.peak_knee_angle);
  const targetThresholdPoints = currentSession.reps.map(() => meta.targetVal);

  // 2. ROM Trend Data: across all real sessions
  const trendSessions = [...allSessions].reverse(); // chronological order
  const trendLabels = trendSessions.map((_, idx) => `Session ${idx + 1}`);
  const trendDataPoints = trendSessions.map((s) => s.avg_peak_knee_angle);
  const trendTargetPoints = trendSessions.map(() => meta.targetVal);

  const isTrendMode = viewMode === 'ROM_TREND' && hasMultipleSessions;

  const datasets: any[] = [
    {
      label: isTrendMode ? `Avg Peak ${meta.metricName} (Trend)` : `Peak ${meta.metricName} per Rep`,
      data: isTrendMode ? trendDataPoints : sessionDataPoints,
      borderColor: '#06B6D4',
      backgroundColor: 'rgba(6, 182, 212, 0.12)',
      borderWidth: 2.5,
      tension: 0.3,
      fill: true,
      pointBackgroundColor: '#06B6D4',
      pointBorderColor: '#FFFFFF',
      pointBorderWidth: 2,
      pointRadius: 5,
      pointHoverRadius: 7
    },
    {
      label: meta.targetLabel,
      data: isTrendMode ? trendTargetPoints : targetThresholdPoints,
      borderColor: 'rgba(16, 185, 129, 0.6)',
      borderWidth: 1.5,
      borderDash: [6, 4],
      pointRadius: 0,
      fill: false
    }
  ];

  const chartData = {
    labels: isTrendMode ? trendLabels : sessionLabels,
    datasets
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        labels: {
          color: '#94A3B8',
          font: { family: 'Inter', size: 11 },
          boxWidth: 12
        }
      },
      tooltip: {
        backgroundColor: '#0F172A',
        titleColor: '#F8FAFC',
        bodyColor: '#94A3B8',
        borderColor: '#334155',
        borderWidth: 1,
        padding: 10,
        boxPadding: 4,
        callbacks: {
          label: (context: any) => `${context.dataset.label}: ${context.parsed.y}°`
        }
      }
    },
    scales: {
      x: {
        grid: {
          color: 'rgba(255, 255, 255, 0.06)'
        },
        ticks: {
          color: '#94A3B8',
          font: { family: 'Inter', size: 11 }
        }
      },
      y: {
        title: {
          display: true,
          text: `${meta.metricName} (°)`,
          color: '#64748B',
          font: { family: 'Inter', size: 11 }
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.06)'
        },
        ticks: {
          color: '#94A3B8',
          font: { family: 'Inter', size: 11 },
          callback: (value: any) => `${value}°`
        },
        min: 40,
        max: 180
      }
    }
  };

  return (
    <div>
      <div className="chart-header">
        <h4 style={{ fontSize: '1rem', color: '#F8FAFC' }}>
          {isTrendMode ? 'LONGITUDINAL ROM TREND' : 'SESSION ROM KINEMATICS'}
        </h4>

        {hasMultipleSessions ? (
          <div className="chart-toggle-group">
            <button
              className={`chart-toggle-btn ${viewMode === 'SESSION_ROM' ? 'active' : ''}`}
              onClick={() => onToggleViewMode('SESSION_ROM')}
            >
              Session ROM
            </button>
            <button
              className={`chart-toggle-btn ${viewMode === 'ROM_TREND' ? 'active' : ''}`}
              onClick={() => onToggleViewMode('ROM_TREND')}
            >
              ROM Trend ({allSessions.length} Sessions)
            </button>
          </div>
        ) : (
          <span style={{ fontSize: '0.75rem', color: '#64748B', fontStyle: 'italic' }}>
            {allSessions.length === 1
              ? 'One session recorded. More sessions are needed for longitudinal trends.'
              : 'Showing SESSION ROM'}
          </span>
        )}
      </div>

      <div className="chart-canvas-wrapper" style={{ height: '300px' }}>
        {chartData.labels.length > 0 ? (
          <Line data={chartData} options={chartOptions} />
        ) : (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#64748B' }}>
            No reps recorded in this session.
          </div>
        )}
      </div>
    </div>
  );
};

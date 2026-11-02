import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { KpiCards } from '../components/KpiCards';
import type { SessionSummary } from '../types/session';

describe('KpiCards Component', () => {
  it('renders default empty placeholders when no sessions exist', () => {
    render(<KpiCards sessions={[]} />);

    expect(screen.getByText('Recorded Sessions')).toBeInTheDocument();
    expect(screen.getAllByText('0')).toHaveLength(3);
    expect(screen.getByText('Awaiting first session')).toBeInTheDocument();
    expect(screen.getByText('--°')).toBeInTheDocument();
  });

  it('renders computed kinematic statistics accurately for recorded sessions', () => {
    const mockSessions: SessionSummary[] = [
      {
        id: 'sess-1',
        device_id: 'phone-pixel',
        exercise_name: 'squat',
        started_at: 1000000,
        completed_at: 1060000,
        duration_seconds: 60,
        total_reps: 10,
        valid_reps: 8,
        avg_peak_knee_angle: 85.0,
        min_knee_angle: 80.0,
        max_knee_angle: 175.0,
        evidence_failure_count: 2,
        created_at: '2026-11-02T10:00:00Z',
      },
      {
        id: 'sess-2',
        device_id: 'phone-pixel',
        exercise_name: 'squat',
        started_at: 2000000,
        completed_at: 2060000,
        duration_seconds: 60,
        total_reps: 10,
        valid_reps: 7,
        avg_peak_knee_angle: 95.0,
        min_knee_angle: 75.0,
        max_knee_angle: 180.0,
        evidence_failure_count: 1,
        created_at: '2026-11-02T11:00:00Z',
      },
    ];

    render(<KpiCards sessions={mockSessions} />);

    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('15')).toBeInTheDocument();
    expect(screen.getByText('90°')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });
});

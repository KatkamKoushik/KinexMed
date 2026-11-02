export interface Rep {
  id: string;
  session_id: string;
  rep_number: number;
  is_valid: boolean;
  start_timestamp_ms: number;
  peak_timestamp_ms: number;
  end_timestamp_ms: number;
  duration_ms: number;
  peak_knee_angle: number;
  start_knee_angle: number;
  end_knee_angle: number;
  feedback_message: string;
  failure_reasons: string[];
}

export interface SessionSummary {
  id: string;
  device_id: string;
  exercise_name: string;
  started_at: number;
  completed_at: number;
  duration_seconds: number;
  total_reps: number;
  valid_reps: number;
  avg_peak_knee_angle: number;
  min_knee_angle: number;
  max_knee_angle: number;
  evidence_failure_count: number;
  created_at: string;
}

export interface EvidenceEvent {
  id: string;
  session_id: string;
  event_type: string;
  start_time_ms: number;
  end_time_ms: number;
  duration_ms: number;
  frame_count: number;
  details: string;
}

export interface SessionDetail extends SessionSummary {
  reps: Rep[];
  evidence_events?: EvidenceEvent[];
}

export interface SessionNlSummary {
  source: string;
  summary: string;
  disclaimer: string;
}

export interface HealthStatus {
  status: string;
  database: string;
  service: string;
  version: string;
}

export interface PlanExercise {
  id?: string;
  exercise_type: string;
  target_sets: number;
  target_reps: number;
  order_index: number;
}

export interface Plan {
  id: string;
  name: string;
  description?: string;
  is_active: boolean;
  frequency_per_week: number;
  created_at: string;
  updated_at?: string;
  exercises: PlanExercise[];
}

export interface SessionFeedback {
  id: string;
  session_id: string;
  author: string;
  message: string;
  created_at: string;
}

export interface WeeklyReport {
  report_type: string;
  start_date: string;
  completed_sessions: number;
  target_sessions: number;
  goal_met: boolean;
  total_valid_reps: number;
  total_attempted_reps: number;
  adherence_ratio_percent: number;
  total_duration_seconds: number;
  exercise_distribution: Record<string, number>;
}

export interface MonthlyReport {
  report_type: string;
  month: string;
  completed_sessions: number;
  total_valid_reps: number;
  total_attempted_reps: number;
  adherence_ratio_percent: number;
  total_therapy_hours: number;
  exercise_distribution: Record<string, number>;
}

export type ClinicianView =
  | 'OVERVIEW'
  | 'SESSIONS'
  | 'DETAIL'
  | 'REPS'
  | 'ROM'
  | 'EVIDENCE'
  | 'HISTORY'
  | 'PLANS'
  | 'REPORTS';

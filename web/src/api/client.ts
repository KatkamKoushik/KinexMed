import type { SessionSummary, SessionDetail, SessionNlSummary, HealthStatus } from '../types/session';

const API_BASE = import.meta.env.VITE_API_URL || 'http://127.0.0.1:8000';

export async function checkApiHealth(): Promise<boolean> {
  try {
    const res = await fetch(`${API_BASE}/health`, { method: 'GET' });
    if (!res.ok) {
      // Fallback check on root
      const rootRes = await fetch(`${API_BASE}/`, { method: 'GET' });
      return rootRes.ok;
    }
    const data = await res.json();
    return data.status === 'healthy';
  } catch {
    return false;
  }
}

export async function fetchHealthStatus(): Promise<HealthStatus | null> {
  try {
    const res = await fetch(`${API_BASE}/health`, { method: 'GET' });
    if (!res.ok) return null;
    return await res.json();
  } catch {
    return null;
  }
}

export async function fetchSessions(exerciseName?: string, deviceId?: string): Promise<SessionSummary[]> {
  const params = new URLSearchParams();
  if (exerciseName) params.append('exercise_name', exerciseName);
  if (deviceId) params.append('device_id', deviceId);

  const url = `${API_BASE}/sessions${params.toString() ? '?' + params.toString() : ''}`;
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Failed to fetch sessions: ${res.statusText}`);
  }
  return res.json();
}

export async function fetchSessionDetail(id: string): Promise<SessionDetail> {
  const res = await fetch(`${API_BASE}/sessions/${id}`);
  if (!res.ok) {
    throw new Error(`Failed to fetch session detail: ${res.statusText}`);
  }
  return res.json();
}

export async function fetchSessionSummary(id: string): Promise<SessionNlSummary> {
  const res = await fetch(`${API_BASE}/sessions/${id}/summary`);
  if (!res.ok) {
    throw new Error(`Failed to fetch session summary: ${res.statusText}`);
  }
  return res.json();
}

// Plan API functions
export async function fetchPlans(activeOnly = false): Promise<import('../types/session').Plan[]> {
  const url = `${API_BASE}/plans${activeOnly ? '?active_only=true' : ''}`;
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Failed to fetch plans: ${res.statusText}`);
  }
  return res.json();
}

export async function createPlan(plan: Partial<import('../types/session').Plan>): Promise<import('../types/session').Plan> {
  const res = await fetch(`${API_BASE}/plans`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(plan),
  });
  if (!res.ok) {
    throw new Error(`Failed to create plan: ${res.statusText}`);
  }
  return res.json();
}

export async function updatePlan(
  planId: string,
  plan: Partial<import('../types/session').Plan>
): Promise<import('../types/session').Plan> {
  const res = await fetch(`${API_BASE}/plans/${planId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(plan),
  });
  if (!res.ok) {
    throw new Error(`Failed to update plan: ${res.statusText}`);
  }
  return res.json();
}

export async function deletePlan(planId: string): Promise<void> {
  const res = await fetch(`${API_BASE}/plans/${planId}`, {
    method: 'DELETE',
  });
  if (!res.ok) {
    throw new Error(`Failed to delete plan: ${res.statusText}`);
  }
}

// Clinician Feedback API functions
export async function fetchSessionFeedback(sessionId: string): Promise<import('../types/session').SessionFeedback[]> {
  const res = await fetch(`${API_BASE}/feedback/session/${sessionId}`);
  if (!res.ok) {
    throw new Error(`Failed to fetch session feedback: ${res.statusText}`);
  }
  return res.json();
}

export async function submitFeedback(
  sessionId: string,
  author: string,
  message: string
): Promise<import('../types/session').SessionFeedback> {
  const res = await fetch(`${API_BASE}/feedback`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ session_id: sessionId, author, message }),
  });
  if (!res.ok) {
    throw new Error(`Failed to submit feedback: ${res.statusText}`);
  }
  return res.json();
}

// Clinical Reports API functions
export async function fetchWeeklyReport(targetSessions = 5): Promise<import('../types/session').WeeklyReport> {
  const res = await fetch(`${API_BASE}/reports/weekly?target_sessions=${targetSessions}`);
  if (!res.ok) {
    throw new Error(`Failed to fetch weekly report: ${res.statusText}`);
  }
  return res.json();
}

export async function fetchMonthlyReport(): Promise<import('../types/session').MonthlyReport> {
  const res = await fetch(`${API_BASE}/reports/monthly`);
  if (!res.ok) {
    throw new Error(`Failed to fetch monthly report: ${res.statusText}`);
  }
  return res.json();
}

export async function fetchSessionReport(sessionId: string): Promise<Record<string, unknown>> {
  const res = await fetch(`${API_BASE}/reports/session/${sessionId}`);
  if (!res.ok) {
    throw new Error(`Failed to fetch session report: ${res.statusText}`);
  }
  return res.json();
}


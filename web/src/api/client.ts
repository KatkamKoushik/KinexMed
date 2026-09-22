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

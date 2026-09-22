import React from 'react';
import type { ClinicianView } from '../types/session';
import {
  LayoutDashboard,
  ListOrdered,
  FileText,
  Activity,
  LineChart,
  ShieldCheck,
  History
} from 'lucide-react';

interface SidebarProps {
  currentView: ClinicianView;
  onSelectView: (view: ClinicianView) => void;
  sessionCount: number;
  hasSelectedSession: boolean;
}

export const Sidebar: React.FC<SidebarProps> = ({
  currentView,
  onSelectView,
  sessionCount,
  hasSelectedSession
}) => {
  const navItems: { id: ClinicianView; label: string; icon: React.ReactNode; requiresSession?: boolean }[] = [
    { id: 'OVERVIEW', label: 'Overview', icon: <LayoutDashboard size={18} /> },
    { id: 'SESSIONS', label: 'Session List', icon: <ListOrdered size={18} /> },
    { id: 'DETAIL', label: 'Session Detail', icon: <FileText size={18} />, requiresSession: true },
    { id: 'REPS', label: 'Rep Analysis', icon: <Activity size={18} />, requiresSession: true },
    { id: 'ROM', label: 'ROM Visualization', icon: <LineChart size={18} /> },
    { id: 'EVIDENCE', label: 'Evidence Quality', icon: <ShieldCheck size={18} /> },
    { id: 'HISTORY', label: 'Patient History', icon: <History size={18} /> }
  ];

  return (
    <aside className="clinician-sidebar">
      <div className="sidebar-brand">
        <div className="brand-logo-pill">KM</div>
        <div className="brand-info">
          <h3>KinexMed</h3>
          <span>Clinician Portal</span>
        </div>
      </div>

      <nav className="sidebar-nav">
        {navItems.map((item) => {
          const isActive = currentView === item.id;
          const isDisabled = item.requiresSession && !hasSelectedSession && sessionCount > 0;

          return (
            <button
              key={item.id}
              className={`nav-item-btn ${isActive ? 'active' : ''} ${isDisabled ? 'disabled' : ''}`}
              onClick={() => {
                if (!isDisabled) {
                  onSelectView(item.id);
                }
              }}
              disabled={isDisabled}
              title={isDisabled ? 'Select a session first' : item.label}
            >
              <span className="nav-icon">{item.icon}</span>
              <span className="nav-label">{item.label}</span>
              {item.id === 'SESSIONS' && sessionCount > 0 && (
                <span className="nav-count-badge">{sessionCount}</span>
              )}
            </button>
          );
        })}
      </nav>

      <div className="sidebar-footer">
        <div className="telemetry-badge">
          <span className="telemetry-dot"></span>
          <span>Edge Telemetry</span>
        </div>
        <p className="sidebar-subtext">Zero raw video transmission. 100% on-device kinematic geometry.</p>
      </div>
    </aside>
  );
};

import React from 'react';
import { Activity, RefreshCw, Smartphone } from 'lucide-react';

interface NavbarProps {
  apiHealthy: boolean;
  isSyncing: boolean;
  onRefresh: () => void;
  latestDeviceId?: string;
}

export const Navbar: React.FC<NavbarProps> = ({ apiHealthy, isSyncing, onRefresh, latestDeviceId }) => {
  return (
    <header className="navbar">
      <div className="nav-brand">
        <div className="brand-icon">
          <Activity size={20} color="#FFFFFF" />
        </div>
        <span className="brand-title">KinexMed</span>
        <span className="brand-badge">Clinician Portal</span>
      </div>

      <div className="nav-actions">
        {latestDeviceId ? (
          <div className="status-indicator">
            <Smartphone size={14} color="#38BDF8" />
            <span>Device: {latestDeviceId}</span>
          </div>
        ) : (
          <div className="status-indicator" style={{ opacity: 0.6 }}>
            <Smartphone size={14} color="#64748B" />
            <span style={{ color: '#94A3B8' }}>No Ingested Device Telemetry</span>
          </div>
        )}

        <div className="status-indicator">
          <div className={`status-dot ${apiHealthy ? '' : 'offline'}`} />
          <span>{apiHealthy ? 'Backend Active' : 'Backend Disconnected'}</span>
        </div>

        <button 
          className="btn-sync" 
          onClick={onRefresh} 
          disabled={isSyncing}
          title="Refresh real-time session data from FastAPI backend"
        >
          <RefreshCw size={14} className={isSyncing ? 'spin' : ''} />
          <span>{isSyncing ? 'Syncing...' : 'Refresh'}</span>
        </button>
      </div>
    </header>
  );
};

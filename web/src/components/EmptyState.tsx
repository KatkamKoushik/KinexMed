import React from 'react';
import { Camera, CheckCircle2, ShieldAlert } from 'lucide-react';

export const EmptyState: React.FC = () => {
  return (
    <div className="glass-card empty-state-card">
      <div className="empty-icon-wrap">
        <Camera size={36} />
      </div>
      <h2 className="empty-title">No sessions recorded yet.</h2>
      <p className="empty-description">
        KinexMed does not seed fake patient data or synthetic metrics. Once a prescribed exercise session is completed on your Android phone, real kinematic measurements will appear here.
      </p>

      <div className="empty-steps">
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#38BDF8', fontWeight: 600 }}>
          <CheckCircle2 size={16} />
          <span>Real-time On-Device Flow:</span>
        </div>
        <p>1. Open KinexMed on your Android phone and select <strong>Squat</strong>.</p>
        <p>2. Prop phone upright 2–3 meters away in clear view.</p>
        <p>3. Perform squats. Real CameraX + MediaPipe runs locally.</p>
        <p>4. Tap <strong>Finish Session</strong> to record real metrics.</p>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#94A3B8', marginTop: '4px', fontSize: '0.75rem' }}>
          <ShieldAlert size={14} />
          <span>Privacy: Raw video remains strictly on phone. Only joint kinematics sync.</span>
        </div>
      </div>
    </div>
  );
};

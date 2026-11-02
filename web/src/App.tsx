import { useState, useEffect, useCallback } from 'react';
import type { SessionSummary, SessionDetail, ClinicianView } from './types/session';
import { checkApiHealth, fetchSessions, fetchSessionDetail } from './api/client';
import { Sidebar } from './components/Sidebar';
import { Navbar } from './components/Navbar';
import { OverviewView } from './components/OverviewView';
import { SessionListView } from './components/SessionListView';
import { SessionDetailView } from './components/SessionDetailView';
import { RepAnalysisView } from './components/RepAnalysisView';
import { EvidenceQualityView } from './components/EvidenceQualityView';
import { HistoryView } from './components/HistoryView';
import { RomChart } from './components/RomChart';
import { EmptyState } from './components/EmptyState';
import { PlansView } from './components/PlansView';
import { ReportsView } from './components/ReportsView';
import './App.css';


export function App() {
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  const [selectedDetail, setSelectedDetail] = useState<SessionDetail | null>(null);
  const [currentView, setCurrentView] = useState<ClinicianView>('OVERVIEW');
  const [apiHealthy, setApiHealthy] = useState<boolean>(false);
  const [isSyncing, setIsSyncing] = useState<boolean>(false);
  const [romViewMode, setRomViewMode] = useState<'SESSION_ROM' | 'ROM_TREND'>('SESSION_ROM');

  const loadData = useCallback(async () => {
    setIsSyncing(true);
    const healthy = await checkApiHealth();
    setApiHealthy(healthy);

    if (healthy) {
      try {
        const fetchedSessions = await fetchSessions();
        setSessions(fetchedSessions);

        if (selectedSessionId) {
          try {
            const detail = await fetchSessionDetail(selectedSessionId);
            setSelectedDetail(detail);
          } catch {
            // Selected session might not be present
          }
        } else if (fetchedSessions.length > 0) {
          const firstDetail = await fetchSessionDetail(fetchedSessions[0].id);
          setSelectedSessionId(fetchedSessions[0].id);
          setSelectedDetail(firstDetail);
        }
      } catch (err) {
        console.error('Failed to load sessions:', err);
      }
    }
    setIsSyncing(false);
  }, [selectedSessionId]);

  useEffect(() => {
    loadData();
    // Poll every 5 seconds for new sessions arriving from Android phone
    const interval = setInterval(loadData, 5000);
    return () => clearInterval(interval);
  }, [loadData]);

  const handleSelectSession = async (id: string, targetView: ClinicianView = 'DETAIL') => {
    setSelectedSessionId(id);
    setCurrentView(targetView);
    try {
      const detail = await fetchSessionDetail(id);
      setSelectedDetail(detail);
    } catch (err) {
      console.error('Error fetching detail:', err);
    }
  };

  return (
    <div className="clinician-layout">
      {/* 1. Clinician Portal Navigation Sidebar */}
      <Sidebar
        currentView={currentView}
        onSelectView={setCurrentView}
        sessionCount={sessions.length}
        hasSelectedSession={selectedDetail !== null}
      />

      {/* 2. Main Content Area */}
      <div className="main-content-area">
        <Navbar
          apiHealthy={apiHealthy}
          isSyncing={isSyncing}
          onRefresh={loadData}
          latestDeviceId={sessions.length > 0 ? sessions[0].device_id : undefined}
        />

        <main className="view-container">
          {sessions.length === 0 && currentView === 'OVERVIEW' ? (
            <EmptyState />
          ) : (
            <>
              {currentView === 'OVERVIEW' && (
                <OverviewView
                  sessions={sessions}
                  onSelectSession={(id) => handleSelectSession(id, 'DETAIL')}
                  onViewAllSessions={() => setCurrentView('SESSIONS')}
                />
              )}

              {currentView === 'SESSIONS' && (
                <SessionListView
                  sessions={sessions}
                  selectedId={selectedSessionId}
                  onSelectSession={(id) => handleSelectSession(id, 'DETAIL')}
                />
              )}

              {currentView === 'DETAIL' && (
                selectedDetail ? (
                  <SessionDetailView
                    session={selectedDetail}
                    allSessions={sessions}
                    onInspectReps={() => setCurrentView('REPS')}
                  />
                ) : (
                  <div className="glass-card" style={{ padding: '3rem', textAlign: 'center', color: '#94A3B8' }}>
                    Select a session from the Session List to view its clinical details.
                  </div>
                )
              )}

              {currentView === 'REPS' && (
                <RepAnalysisView
                  session={selectedDetail}
                  onBackToSessions={() => setCurrentView('SESSIONS')}
                />
              )}

              {currentView === 'ROM' && (
                selectedDetail ? (
                  <div className="glass-card" style={{ padding: '2rem' }}>
                    <RomChart
                      currentSession={selectedDetail}
                      allSessions={sessions}
                      viewMode={romViewMode}
                      onToggleViewMode={setRomViewMode}
                    />
                  </div>
                ) : (
                  <div className="glass-card" style={{ padding: '3rem', textAlign: 'center', color: '#94A3B8' }}>
                    Select a session to inspect ROM kinematics.
                  </div>
                )
              )}

              {currentView === 'EVIDENCE' && (
                <EvidenceQualityView sessions={sessions} />
              )}

              {currentView === 'HISTORY' && (
                <HistoryView
                  sessions={sessions}
                  onSelectSession={(id) => handleSelectSession(id, 'DETAIL')}
                />
              )}

              {currentView === 'PLANS' && (
                <PlansView />
              )}

              {currentView === 'REPORTS' && (
                <ReportsView />
              )}
            </>
          )}
        </main>
      </div>
    </div>
  );
}


export default App;

import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
  fallbackTitle?: string;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

/**
 * Checklist 13-B006: React Error Boundary catching unexpected rendering
 * exceptions without tearing down the entire clinical portal.
 */
export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('KinexMed Clinician Portal Error Caught:', error, errorInfo);
  }

  private handleReset = () => {
    this.setState({ hasError: false, error: null });
    window.location.reload();
  };

  public render() {
    if (this.state.hasError) {
      return (
        <div
          className="error-boundary-container"
          role="alert"
          style={{
            padding: '2rem',
            margin: '2rem auto',
            maxWidth: '600px',
            backgroundColor: '#1E293B',
            borderRadius: '12px',
            border: '1px solid #EF4444',
            color: '#F8FAFC',
            textAlign: 'center',
          }}
        >
          <h2 style={{ color: '#F87171', marginBottom: '1rem' }}>
            {this.props.fallbackTitle || 'Clinical Telemetry Render Error'}
          </h2>
          <p style={{ color: '#94A3B8', marginBottom: '1.5rem', fontSize: '0.9rem' }}>
            An unexpected error occurred while rendering patient telemetry. Patient database records
            remain safe and uncorrupted.
          </p>
          {this.state.error && (
            <pre
              style={{
                backgroundColor: '#0F172A',
                padding: '1rem',
                borderRadius: '8px',
                fontSize: '0.8rem',
                textAlign: 'left',
                overflowX: 'auto',
                color: '#FCA5A5',
                marginBottom: '1.5rem',
              }}
            >
              {this.state.error.message}
            </pre>
          )}
          <button
            onClick={this.handleReset}
            style={{
              padding: '0.6rem 1.2rem',
              backgroundColor: '#3B82F6',
              color: '#FFFFFF',
              border: 'none',
              borderRadius: '6px',
              cursor: 'pointer',
              fontWeight: 600,
            }}
          >
            Reload Clinical Dashboard
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}

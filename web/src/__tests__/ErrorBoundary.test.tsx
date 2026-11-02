import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ErrorBoundary } from '../components/ErrorBoundary';

const FaultyComponent = () => {
  throw new Error('Test crash in clinical telemetry widget');
};

const SafeComponent = () => <div>Telemetry data rendered cleanly</div>;

describe('ErrorBoundary Component', () => {
  it('renders children when no errors occur', () => {
    render(
      <ErrorBoundary>
        <SafeComponent />
      </ErrorBoundary>
    );

    expect(screen.getByText('Telemetry data rendered cleanly')).toBeInTheDocument();
  });

  it('catches render errors and renders clinical alert fallback', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    render(
      <ErrorBoundary fallbackTitle="Custom Telemetry Alert">
        <FaultyComponent />
      </ErrorBoundary>
    );

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Custom Telemetry Alert')).toBeInTheDocument();
    expect(screen.getByText('Test crash in clinical telemetry widget')).toBeInTheDocument();
    expect(screen.getByText('Reload Clinical Dashboard')).toBeInTheDocument();

    consoleSpy.mockRestore();
  });
});

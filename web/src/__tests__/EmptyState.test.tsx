import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EmptyState } from '../components/EmptyState';

describe('EmptyState Component', () => {
  it('renders clinical empty state information and privacy guarantees', () => {
    render(<EmptyState />);

    expect(screen.getByText('No sessions recorded yet.')).toBeInTheDocument();
    expect(screen.getByText(/KinexMed does not seed fake patient data/i)).toBeInTheDocument();
    expect(screen.getByText(/Raw video remains strictly on phone/i)).toBeInTheDocument();
  });
});


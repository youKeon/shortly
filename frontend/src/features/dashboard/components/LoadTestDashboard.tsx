import { useState } from 'react';
import { Sidebar } from './Sidebar';
import { PhaseContent } from './PhaseContent';
import type { Phase } from '../types';

interface LoadTestDashboardProps {
  onNavigateToLinks?: () => void;
}

export function LoadTestDashboard({ onNavigateToLinks }: LoadTestDashboardProps) {
  const [selectedPhase, setSelectedPhase] = useState<Phase>('phase1');

  return (
    <div className="dashboard">
      <Sidebar 
        selectedPhase={selectedPhase} 
        onSelectPhase={setSelectedPhase}
        onNavigateToLinks={onNavigateToLinks}
      />
      <main className="dashboard__content">
        <PhaseContent phase={selectedPhase} />
      </main>
    </div>
  );
}


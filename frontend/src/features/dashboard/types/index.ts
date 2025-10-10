export type Phase = 'phase1' | 'phase2' | 'phase3' | 'phase4';

export type TestStatus = 'idle' | 'running' | 'completed' | 'failed';

export interface Goal {
  label: string;
  value: string;
}

export interface Optimization {
  title: string;
  description: string;
  code?: string;
}

export interface PhaseConfig {
  name: string;
  description: string;
  goals: Goal[];
  optimizations: Optimization[];
}


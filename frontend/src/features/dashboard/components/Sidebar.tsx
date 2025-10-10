import { Activity, Database, Layers, ChartNoAxesGantt, Server, Link2 } from 'lucide-react';
import type { Phase } from '../types';

interface SidebarProps {
  selectedPhase: Phase;
  onSelectPhase: (phase: Phase) => void;
  onNavigateToLinks?: () => void;
}

const phases = [
  { id: 'phase1' as Phase, name: 'Phase 1', subtitle: 'DB 최적화', icon: Database },
  { id: 'phase2' as Phase, name: 'Phase 2', subtitle: 'Redis 캐싱', icon: Layers },
  { id: 'phase3' as Phase, name: 'Phase 3', subtitle: 'Kafka 비동기', icon: ChartNoAxesGantt },
  { id: 'phase4' as Phase, name: 'Phase 4', subtitle: '스케일 아웃', icon: Server },
];

export function Sidebar({ selectedPhase, onSelectPhase, onNavigateToLinks }: SidebarProps) {
  return (
    <aside className="sidebar">
      <div className="sidebar__header">
        <div className="sidebar__logo">
          <Activity size={24} />
          <span>Load Tests</span>
        </div>
        {onNavigateToLinks && (
          <button className="sidebar__back-btn" onClick={onNavigateToLinks}>
            <Link2 size={16} />
            <span>Links로 이동</span>
          </button>
        )}
      </div>

      <nav className="sidebar__nav">
        {phases.map((phase) => {
          const Icon = phase.icon;
          const isActive = selectedPhase === phase.id;
          
          return (
            <button
              key={phase.id}
              className={`sidebar__item ${isActive ? 'sidebar__item--active' : ''}`}
              onClick={() => onSelectPhase(phase.id)}
            >
              <Icon size={20} />
              <div className="sidebar__item-text">
                <div className="sidebar__item-name">{phase.name}</div>
                <div className="sidebar__item-subtitle">{phase.subtitle}</div>
              </div>
            </button>
          );
        })}
      </nav>
    </aside>
  );
}


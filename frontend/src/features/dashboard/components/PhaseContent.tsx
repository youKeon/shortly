import { useState } from 'react';
import { Play, Clock, CheckCircle, XCircle } from 'lucide-react';
import { TestProgress } from './TestProgress';
import { OptimizationList } from './OptimizationList';
import type { Phase, TestStatus } from '../types';
import { phaseConfig } from '../config/phaseConfig';

interface PhaseContentProps {
  phase: Phase;
}

export function PhaseContent({ phase }: PhaseContentProps) {
  const [status, setStatus] = useState<TestStatus>('idle');
  const [progress, setProgress] = useState(0);
  
  const config = phaseConfig[phase];

  const handleStartTest = async () => {
    setStatus('running');
    setProgress(0);

    // 진행률 시뮬레이션
    const interval = setInterval(() => {
      setProgress((prev) => {
        if (prev >= 100) {
          clearInterval(interval);
          setStatus('completed');
          return 100;
        }
        return prev + 2;
      });
    }, 180); // 9초 완료 (9분 테스트를 시뮬레이션)
  };

  const getStatusIcon = () => {
    switch (status) {
      case 'running':
        return <Clock size={20} className="icon-running" />;
      case 'completed':
        return <CheckCircle size={20} className="icon-success" />;
      case 'failed':
        return <XCircle size={20} className="icon-error" />;
      default:
        return null;
    }
  };

  return (
    <div className="phase-content">
      {/* Header */}
      <header className="phase-content__header">
        <div>
          <h1 className="phase-content__title">{config.name}</h1>
          <p className="phase-content__subtitle">{config.description}</p>
        </div>
        
        <button
          className="btn btn--primary"
          onClick={handleStartTest}
          disabled={status === 'running'}
        >
          <Play size={18} />
          {status === 'running' ? '테스트 실행 중...' : '테스트 시작'}
        </button>
      </header>

      {/* 목표 */}
      <section className="phase-content__section">
        <h2 className="phase-content__section-title">목표</h2>
        <div className="metrics-grid">
          {config.goals.map((goal) => (
            <div key={goal.label} className="metric-card">
              <div className="metric-card__label">{goal.label}</div>
              <div className="metric-card__value">{goal.value}</div>
            </div>
          ))}
        </div>
      </section>

      {/* 최적화 내용 */}
      <section className="phase-content__section">
        <h2 className="phase-content__section-title">최적화 내용</h2>
        <OptimizationList optimizations={config.optimizations} />
      </section>

      {/* 진행 상태 */}
      {status !== 'idle' && (
        <section className="phase-content__section">
          <div className="status-header">
            <h2 className="phase-content__section-title">테스트 진행 상태</h2>
            <div className="status-badge">
              {getStatusIcon()}
              <span>{status === 'running' ? '실행 중' : status === 'completed' ? '완료' : '실패'}</span>
            </div>
          </div>
          <TestProgress progress={progress} status={status} />
        </section>
      )}

      {/* 결과 (완료 시) */}
      {status === 'completed' && (
        <section className="phase-content__section">
          <h2 className="phase-content__section-title">테스트 결과</h2>
          <div className="result-card">
            <div className="result-card__success">
              <CheckCircle size={48} />
              <h3>테스트 성공!</h3>
              <p>모든 목표를 달성했습니다.</p>
            </div>
            
            <div className="metrics-grid">
              <div className="metric-card metric-card--result">
                <div className="metric-card__label">TPS</div>
                <div className="metric-card__value">4,091</div>
                <div className="metric-card__change metric-card__change--success">+309%</div>
              </div>
              <div className="metric-card metric-card--result">
                <div className="metric-card__label">P95 응답시간</div>
                <div className="metric-card__value">61.6ms</div>
                <div className="metric-card__change metric-card__change--success">목표 달성</div>
              </div>
              <div className="metric-card metric-card--result">
                <div className="metric-card__label">실패율</div>
                <div className="metric-card__value">0.00%</div>
                <div className="metric-card__change metric-card__change--success">완벽</div>
              </div>
            </div>
          </div>
        </section>
      )}
    </div>
  );
}


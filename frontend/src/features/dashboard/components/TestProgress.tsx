import type { TestStatus } from '../types';

interface TestProgressProps {
  progress: number;
  status: TestStatus;
}

export function TestProgress({ progress, status }: TestProgressProps) {
  return (
    <div className="test-progress">
      <div className="test-progress__bar-container">
        <div 
          className={`test-progress__bar test-progress__bar--${status}`}
          style={{ width: `${progress}%` }}
        />
      </div>
      <div className="test-progress__info">
        <span className="test-progress__percentage">{Math.round(progress)}%</span>
        <span className="test-progress__text">
          {status === 'running' ? '테스트 실행 중...' : '테스트 완료'}
        </span>
      </div>
    </div>
  );
}


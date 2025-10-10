import { CheckCircle } from 'lucide-react';
import type { Optimization } from '../types';

interface OptimizationListProps {
  optimizations: Optimization[];
}

export function OptimizationList({ optimizations }: OptimizationListProps) {
  return (
    <div className="optimization-list">
      {optimizations.map((opt, index) => (
        <div key={index} className="optimization-item">
          <div className="optimization-item__header">
            <CheckCircle size={20} className="optimization-item__icon" />
            <h3 className="optimization-item__title">{opt.title}</h3>
          </div>
          <p className="optimization-item__description">{opt.description}</p>
          {opt.code && (
            <pre className="optimization-item__code">
              <code>{opt.code}</code>
            </pre>
          )}
        </div>
      ))}
    </div>
  );
}


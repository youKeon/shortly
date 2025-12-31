import { useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Check, X, AlertCircle, Info } from 'lucide-react';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

interface ToastProps {
  message: string;
  type: ToastType;
  isVisible: boolean;
  onClose: () => void;
  duration?: number;
}

const toastConfig = {
  success: {
    icon: Check,
    bgClass: 'bg-emerald-500',
    textClass: 'text-white',
  },
  error: {
    icon: X,
    bgClass: 'bg-red-500',
    textClass: 'text-white',
  },
  warning: {
    icon: AlertCircle,
    bgClass: 'bg-amber-500',
    textClass: 'text-white',
  },
  info: {
    icon: Info,
    bgClass: 'bg-blue-500',
    textClass: 'text-white',
  },
};

export function Toast({ message, type, isVisible, onClose, duration = 3000 }: ToastProps) {
  useEffect(() => {
    if (isVisible) {
      const timer = setTimeout(onClose, duration);
      return () => clearTimeout(timer);
    }
  }, [isVisible, duration, onClose]);

  const config = toastConfig[type];
  const Icon = config.icon;

  return (
    <AnimatePresence>
      {isVisible && (
        <motion.div
          initial={{ opacity: 0, y: -20, scale: 0.95 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: -20, scale: 0.95 }}
          transition={{ type: 'spring', stiffness: 500, damping: 30 }}
          className="fixed top-8 left-1/2 -translate-x-1/2 z-[100] pointer-events-auto"
        >
          <div className={`flex items-center gap-3 ${config.bgClass} ${config.textClass} px-6 py-4 rounded-xl shadow-2xl backdrop-blur-sm min-w-[300px]`}>
            <div className="bg-white/20 p-1 rounded-full">
              <Icon className="w-5 h-5" />
            </div>
            <span className="font-medium text-base">{message}</span>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

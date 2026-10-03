import { AlertCircle } from 'lucide-react';
import { cn } from '@/lib/utils';

interface ErrorAlertProps extends React.HTMLAttributes<HTMLDivElement> {
  title?: string;
  message?: string;
}

export function ErrorAlert({ title = 'Error', message = 'Something went wrong.', className, ...props }: ErrorAlertProps) {
  return (
    <div
      className={cn(
        'flex items-start gap-3 p-4 mb-4 text-sm text-red-800 rounded-lg bg-red-50',
        className
      )}
      role="alert"
      aria-live="assertive"
      {...props}
    >
      <AlertCircle className="shrink-0 w-5 h-5 mt-0.5" aria-hidden="true" />
      <div>
        <p className="font-medium">{title}</p>
        <p className="mt-1">{message}</p>
      </div>
    </div>
  );
}

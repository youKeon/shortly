import { forwardRef, type InputHTMLAttributes, useId } from 'react';

export interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  hint?: string;
  error?: string | null;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(
  ({ label, hint, error, id, className, ...props }, ref) => {
    const generatedId = useId();
    const inputId = id ?? props.name ?? generatedId;
    const invalid = Boolean(error);
    const fieldClassName = ['textfield', invalid ? 'textfield--error' : '', className ?? '']
      .filter(Boolean)
      .join(' ');
    const messageId = `${inputId}-message`;
    const describedBy = error || hint ? messageId : undefined;

    return (
      <label className={fieldClassName} htmlFor={inputId}>
        {label && <span className="textfield__label">{label}</span>}
        <input
          id={inputId}
          ref={ref}
          className="textfield__input"
          aria-invalid={invalid}
          aria-describedby={describedBy}
          {...props}
        />
        {error ? (
          <span id={messageId} role="alert" className="textfield__error">
            {error}
          </span>
        ) : (
          hint && (
            <span id={messageId} className="textfield__hint">
              {hint}
            </span>
          )
        )}
      </label>
    );
  }
);

TextField.displayName = 'TextField';

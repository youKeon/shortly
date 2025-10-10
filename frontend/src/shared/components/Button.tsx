import type { ButtonHTMLAttributes, PropsWithChildren } from 'react';
interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary';
  block?: boolean;
}

export function Button({
  children,
  className,
  variant = 'primary',
  block = false,
  ...props
}: PropsWithChildren<ButtonProps>) {
  const classes = [
    'button',
    `button--${variant}`,
    block ? 'button--block' : '',
    className ?? ''
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button className={classes} {...props}>
      {children}
    </button>
  );
}

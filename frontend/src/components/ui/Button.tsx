import { cva, type VariantProps } from 'class-variance-authority';
import type { ComponentProps } from 'react';
import { Link, type LinkProps } from 'react-router';
import { cn } from '@/lib/cn';

export const buttonVariants = cva(
  [
    'inline-flex items-center justify-center gap-2 rounded-full font-medium whitespace-nowrap select-none',
    'transition-[background-color,color,border-color,box-shadow,transform] duration-(--duration-base) ease-(--ease-out-soft)',
    'focus-visible:outline-2 focus-visible:outline-offset-2 active:scale-[0.98]',
    'disabled:pointer-events-none disabled:opacity-50',
  ],
  {
    variants: {
      variant: {
        primary: 'bg-ink text-paper hover:bg-ink-soft focus-visible:outline-ink',
        accent: 'bg-ember-500 text-paper hover:bg-ember-600 focus-visible:outline-ember-500',
        secondary:
          'border border-ink/15 bg-transparent text-ink hover:border-ink/40 hover:bg-ink/[0.03] focus-visible:outline-ink',
        light: 'bg-paper text-ink hover:bg-white focus-visible:outline-paper',
        'outline-light':
          'border border-paper/30 bg-transparent text-paper hover:border-paper/70 hover:bg-paper/5 focus-visible:outline-paper',
        ghost: 'bg-transparent text-ink hover:bg-ink/5 focus-visible:outline-ink',
        link: 'h-auto rounded-none px-0 text-ink underline-offset-4 hover:underline',
      },
      size: {
        sm: 'h-9 px-4 text-sm',
        md: 'h-11 px-6 text-[0.9375rem]',
        lg: 'h-13 px-8 text-base',
        icon: 'size-11',
      },
    },
    compoundVariants: [{ variant: 'link', className: 'h-auto px-0' }],
    defaultVariants: { variant: 'primary', size: 'md' },
  },
);

export type ButtonVariantProps = VariantProps<typeof buttonVariants>;

export function Button({
  className,
  variant,
  size,
  type = 'button',
  ...props
}: ComponentProps<'button'> & ButtonVariantProps) {
  return <button type={type} className={cn(buttonVariants({ variant, size }), className)} {...props} />;
}

/** A router link styled as a button. */
export function ButtonLink({ className, variant, size, ...props }: LinkProps & ButtonVariantProps) {
  return <Link className={cn(buttonVariants({ variant, size }), className)} {...props} />;
}

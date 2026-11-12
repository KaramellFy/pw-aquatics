import { cva, type VariantProps } from 'class-variance-authority';
import type { ComponentProps } from 'react';
import { Link, type LinkProps } from 'react-router';
import { cn } from '@/lib/cn';

export const buttonVariants = cva(
  [
    'inline-flex items-center justify-center gap-2 rounded-full font-medium whitespace-nowrap select-none',
    // Colours fade in 200 ms; the press (scale to 97 %) reacts faster, in 120 ms.
    '[transition:background-color_200ms_var(--ease-out),color_200ms_var(--ease-out),border-color_200ms_var(--ease-out),box-shadow_200ms_var(--ease-out),scale_120ms_var(--ease-out)]',
    'focus-visible:outline-2 focus-visible:outline-offset-2 active:scale-[0.97]',
    'disabled:pointer-events-none disabled:opacity-50',
  ],
  {
    variants: {
      variant: {
        /** Main call to action: navy with white text (15.8:1). */
        primary: 'bg-ink text-white hover:bg-ink-soft focus-visible:outline-ink',
        /** Brand accent: baby blue with navy text (8:1). */
        accent: 'bg-brand-400 text-ink hover:bg-brand-300 focus-visible:outline-brand-700',
        secondary:
          'border border-ink/20 bg-surface text-ink hover:border-ink/45 hover:bg-mist focus-visible:outline-ink',
        /** For dark (navy) backgrounds. */
        inverse: 'bg-white text-ink hover:bg-brand-50 focus-visible:outline-white',
        'outline-inverse':
          'border border-white/35 bg-transparent text-white hover:border-white/70 hover:bg-white/5 focus-visible:outline-white',
        ghost: 'bg-transparent text-ink hover:bg-ink/5 focus-visible:outline-ink',
        link: 'h-auto rounded-none px-0 text-brand-700 underline-offset-4 hover:underline',
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

import { cva, type VariantProps } from 'class-variance-authority';
import type { ComponentProps, ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { Container } from './Container';

const sectionVariants = cva('relative', {
  variants: {
    tone: {
      white: 'bg-surface text-ink',
      mist: 'bg-mist text-ink',
      ice: 'bg-ice text-ink',
      ink: 'bg-ink text-white',
    },
    spacing: {
      default: 'py-24 sm:py-32 lg:py-40',
      compact: 'py-16 sm:py-20 lg:py-24',
      none: '',
    },
  },
  defaultVariants: { tone: 'white', spacing: 'default' },
});

type SectionProps = ComponentProps<'section'> &
  VariantProps<typeof sectionVariants> & { contained?: boolean };

/** A full-width page section with consistent vertical rhythm. */
export function Section({ className, tone, spacing, contained = true, children, ...props }: SectionProps) {
  return (
    <section className={cn(sectionVariants({ tone, spacing }), className)} {...props}>
      {contained ? <Container>{children}</Container> : children}
    </section>
  );
}

interface SectionHeadingProps {
  eyebrow?: string;
  title: ReactNode;
  lead?: ReactNode;
  align?: 'start' | 'center';
  as?: 'h1' | 'h2';
  className?: string;
  /** Visual size; h1 page headings use "display", section headings "headline". */
  size?: 'display' | 'headline';
}

export function SectionHeading({
  eyebrow,
  title,
  lead,
  align = 'start',
  as: Heading = 'h2',
  size = 'headline',
  className,
}: SectionHeadingProps) {
  return (
    <div className={cn('max-w-3xl', align === 'center' && 'mx-auto text-center', className)}>
      {eyebrow && <p className="mb-5 eyebrow text-brand-700">{eyebrow}</p>}
      <Heading className={cn('text-balance', size === 'display' ? 'text-display' : 'text-headline')}>
        {title}
      </Heading>
      {lead && <p className="mt-6 text-lg leading-relaxed text-pretty text-current/75">{lead}</p>}
    </div>
  );
}

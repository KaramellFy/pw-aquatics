import { useEffect, useRef, useState, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

interface RevealProps {
  children: ReactNode;
  className?: string;
  /** Delay in ms, for gently staggered groups. */
  delay?: number;
  as?: 'div' | 'li';
}

/**
 * Fades content up once it scrolls into view. Without IntersectionObserver
 * (or with reduced motion, handled globally in CSS) content is simply shown.
 */
export function Reveal({ children, className, delay = 0, as: Tag = 'div' }: RevealProps) {
  const ref = useRef<HTMLDivElement & HTMLLIElement>(null);
  const [visible, setVisible] = useState(() => typeof IntersectionObserver === 'undefined');

  useEffect(() => {
    const element = ref.current;
    if (!element || visible) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin: '0px 0px -10% 0px', threshold: 0.1 },
    );
    observer.observe(element);
    return () => observer.disconnect();
  }, [visible]);

  return (
    <Tag
      ref={ref}
      style={{ animationDelay: `${delay}ms` }}
      className={cn(visible ? 'animate-fade-up' : 'opacity-0', className)}
    >
      {children}
    </Tag>
  );
}

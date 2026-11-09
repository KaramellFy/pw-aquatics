import type { ComponentProps } from 'react';
import { cn } from '@/lib/cn';

/** Centres content with the site's max width and responsive side padding. */
export function Container({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('mx-auto w-full max-w-[84rem] px-5 sm:px-8 lg:px-12', className)} {...props} />;
}

import { Fragment } from 'react';
import { cn } from '@/lib/cn';

/**
 * A heading whose words rise into place one after another, each from behind its own mask
 * (30 ms apart). Pure CSS, so the text is in the page from the start and readable without
 * JavaScript; with reduced motion it is simply shown. Words are separated by ordinary
 * spaces, so the text reads and wraps exactly like the plain sentence.
 */
export function MaskedWords({
  text,
  className,
  delay = 0,
}: {
  text: string;
  className?: string;
  delay?: number;
}) {
  const words = text.split(' ');
  return (
    <span className={cn('block', className)}>
      {words.map((word, index) => (
        <Fragment key={`${word}-${index}`}>
          <span className="mask inline-block align-bottom">
            <span className="mask-line" style={{ animationDelay: `${delay + index * 30}ms` }}>
              {word}
            </span>
          </span>
          {index < words.length - 1 && ' '}
        </Fragment>
      ))}
    </span>
  );
}

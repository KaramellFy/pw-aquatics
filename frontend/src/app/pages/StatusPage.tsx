import type { ReactNode } from 'react';
import { Artwork, type ArtworkMotif, type ArtworkTone } from '@/components/ui/Artwork';
import { Container } from '@/components/ui/Container';

interface StatusPageProps {
  eyebrow: string;
  title: string;
  text: string;
  actions?: ReactNode;
  tone?: ArtworkTone;
  motif?: ArtworkMotif;
}

/** Shared layout for 404, error and "coming soon" pages. */
export function StatusPage({
  eyebrow,
  title,
  text,
  actions,
  tone = 'deep',
  motif = 'shrimp',
}: StatusPageProps) {
  return (
    <Container className="grid items-center gap-12 py-20 sm:py-28 lg:grid-cols-12 lg:py-36">
      <div className="lg:col-span-6">
        <p className="mb-5 eyebrow text-muted">{eyebrow}</p>
        <h1 className="text-headline text-balance">{title}</h1>
        <p className="mt-6 max-w-lg text-lg leading-relaxed text-ink/70">{text}</p>
        {actions && <div className="mt-10 flex flex-wrap gap-3">{actions}</div>}
      </div>
      <Artwork tone={tone} motif={motif} className="aspect-[4/3] rounded-card lg:col-span-5 lg:col-start-8" />
    </Container>
  );
}

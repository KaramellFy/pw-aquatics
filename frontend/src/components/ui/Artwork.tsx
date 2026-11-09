import { Fish, Leaf, Shell, Shrimp, Snail, Waves, type LucideIcon } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';

/**
 * Art-directed placeholder used until real product photography exists.
 * Layered "underwater light" gradients, film grain and a faint line motif.
 * Replace usages with <ResponsiveImage> as soon as photos are available.
 */

export type ArtworkTone = 'deep' | 'moss' | 'sand' | 'ember' | 'stone' | 'night';
export type ArtworkMotif = 'shrimp' | 'snail' | 'shell' | 'leaf' | 'fish' | 'waves' | 'none';

const tones: Record<ArtworkTone, string> = {
  deep: 'bg-deep-800 [background-image:radial-gradient(120%_80%_at_20%_0%,rgb(92_160_160/0.45),transparent_60%),radial-gradient(90%_70%_at_90%_100%,rgb(16_70_74/0.9),transparent_70%)]',
  night:
    'bg-deep-900 [background-image:radial-gradient(80%_60%_at_70%_10%,rgb(60_120_125/0.35),transparent_65%),radial-gradient(70%_60%_at_10%_100%,rgb(30_60_50/0.6),transparent_70%)]',
  moss: 'bg-moss-700 [background-image:radial-gradient(110%_80%_at_80%_0%,rgb(170_195_150/0.45),transparent_60%),radial-gradient(80%_70%_at_0%_100%,rgb(20_40_28/0.8),transparent_70%)]',
  sand: 'bg-sand [background-image:radial-gradient(100%_80%_at_20%_10%,rgb(255_255_255/0.75),transparent_60%),radial-gradient(90%_70%_at_100%_100%,rgb(190_180_160/0.6),transparent_70%)]',
  ember:
    'bg-ember-600 [background-image:radial-gradient(100%_80%_at_75%_5%,rgb(240_170_130/0.55),transparent_60%),radial-gradient(80%_70%_at_0%_100%,rgb(90_30_18/0.7),transparent_70%)]',
  stone:
    'bg-stone-300 [background-image:radial-gradient(100%_80%_at_30%_0%,rgb(255_255_255/0.6),transparent_60%),radial-gradient(90%_70%_at_100%_100%,rgb(120_125_118/0.55),transparent_70%)]',
};

const motifs: Record<Exclude<ArtworkMotif, 'none'>, LucideIcon> = {
  shrimp: Shrimp,
  snail: Snail,
  shell: Shell,
  leaf: Leaf,
  fish: Fish,
  waves: Waves,
};

const lightTones = new Set<ArtworkTone>(['sand', 'stone']);

const GRAIN =
  "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.55'/%3E%3C/svg%3E\")";

interface ArtworkProps {
  tone?: ArtworkTone;
  motif?: ArtworkMotif;
  /** Accessible description; omit for purely decorative use. */
  label?: string;
  className?: string;
  /** Slowly drifting light, for large hero areas. */
  animated?: boolean;
}

export function Artwork({ tone = 'deep', motif = 'none', label, className, animated = false }: ArtworkProps) {
  const { t } = useTranslation();
  const Motif = motif === 'none' ? null : motifs[motif];
  const light = lightTones.has(tone);

  return (
    <div
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      title={label ? `${label} — ${t('common.imagePlaceholder')}` : undefined}
      className={cn('relative isolate overflow-hidden', className)}
    >
      <div className={cn('absolute -inset-[6%]', tones[tone], animated && 'animate-drift')} />
      {/* soft caustic light from the surface */}
      <div className="absolute inset-0 bg-[radial-gradient(60%_35%_at_50%_0%,rgb(255_255_255/0.18),transparent_70%)]" />
      <div
        className="absolute inset-0 opacity-[0.22] mix-blend-overlay"
        style={{ backgroundImage: GRAIN, backgroundSize: '160px 160px' }}
      />
      {Motif && (
        <Motif
          aria-hidden
          strokeWidth={0.6}
          className={cn(
            'absolute top-1/2 left-1/2 size-[46%] -translate-x-1/2 -translate-y-1/2',
            light ? 'text-ink/25' : 'text-paper/30',
          )}
        />
      )}
    </div>
  );
}

import { Fish, Leaf, Shell, Shrimp, Snail, Waves, type LucideIcon } from 'lucide-react';
import type { ComponentType } from 'react';
import { useTranslation } from 'react-i18next';
import { BrandMark } from '@/components/layout/Brand';
import { cn } from '@/lib/cn';

/**
 * Art-directed placeholder used until real product photography exists:
 * layered "light through water" gradients, fine grain and a faint line motif.
 * Replace usages with <ResponsiveImage> as soon as photos are available.
 */

export type ArtworkTone = 'ice' | 'sky' | 'lagoon' | 'deep' | 'pearl' | 'aqua';
export type ArtworkMotif = 'shrimp' | 'snail' | 'shell' | 'leaf' | 'fish' | 'waves' | 'brand' | 'none';

const tones: Record<ArtworkTone, string> = {
  ice: 'bg-ice [background-image:radial-gradient(110%_80%_at_20%_0%,rgb(255_255_255/0.95),transparent_60%),radial-gradient(90%_70%_at_100%_100%,rgb(167_208_229/0.75),transparent_70%)]',
  sky: 'bg-brand-300 [background-image:radial-gradient(110%_80%_at_25%_0%,rgb(242_248_252/0.85),transparent_60%),radial-gradient(90%_70%_at_100%_100%,rgb(95_166_201/0.8),transparent_70%)]',
  lagoon:
    'bg-brand-600 [background-image:radial-gradient(110%_80%_at_20%_0%,rgb(167_208_229/0.7),transparent_60%),radial-gradient(90%_70%_at_100%_100%,rgb(24_62_82/0.85),transparent_70%)]',
  deep: 'bg-brand-900 [background-image:radial-gradient(100%_75%_at_70%_0%,rgb(95_166_201/0.55),transparent_62%),radial-gradient(80%_70%_at_0%_100%,rgb(15_37_51/0.9),transparent_70%)]',
  pearl:
    'bg-[#eef2f4] [background-image:radial-gradient(100%_80%_at_30%_0%,rgb(255_255_255/0.95),transparent_60%),radial-gradient(90%_70%_at_100%_100%,rgb(190_205_214/0.8),transparent_70%)]',
  aqua: 'bg-[#6fb3c4] [background-image:radial-gradient(100%_80%_at_75%_5%,rgb(226_240_248/0.8),transparent_60%),radial-gradient(80%_70%_at_0%_100%,rgb(34_85_111/0.75),transparent_70%)]',
};

const motifs: Record<Exclude<ArtworkMotif, 'none'>, LucideIcon | ComponentType<{ className?: string }>> = {
  shrimp: Shrimp,
  snail: Snail,
  shell: Shell,
  leaf: Leaf,
  fish: Fish,
  waves: Waves,
  brand: BrandMark,
};

const lightTones = new Set<ArtworkTone>(['ice', 'pearl', 'sky']);

const GRAIN =
  "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.5'/%3E%3C/svg%3E\")";

interface ArtworkProps {
  tone?: ArtworkTone;
  motif?: ArtworkMotif;
  /** Accessible description; omit for purely decorative use. */
  label?: string;
  className?: string;
  /** Optional colour glow from data (e.g. a product's colour), as a CSS colour value. */
  accentColor?: string | null;
}

export function Artwork({ tone = 'sky', motif = 'none', label, className, accentColor }: ArtworkProps) {
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
      <div className={cn('absolute -inset-[6%]', tones[tone])} />
      {accentColor && (
        <div
          className="absolute inset-0 opacity-45 mix-blend-multiply"
          style={{ backgroundImage: `radial-gradient(70% 55% at 50% 60%, ${accentColor}, transparent 70%)` }}
        />
      )}
      {/* soft light from the water surface */}
      <div className="absolute inset-0 bg-[radial-gradient(60%_35%_at_50%_0%,rgb(255_255_255/0.22),transparent_70%)]" />
      <div
        className="absolute inset-0 opacity-[0.16] mix-blend-overlay"
        style={{ backgroundImage: GRAIN, backgroundSize: '160px 160px' }}
      />
      {Motif && (
        <Motif
          aria-hidden
          strokeWidth={0.6}
          className={cn(
            'absolute top-1/2 left-1/2 size-[46%] -translate-x-1/2 -translate-y-1/2',
            motif === 'brand' && 'size-[38%]',
            light ? 'text-brand-800/25' : 'text-white/35',
          )}
        />
      )}
    </div>
  );
}

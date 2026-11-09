import { useTranslation } from 'react-i18next';
import { PageMeta } from '@/lib/seo/PageMeta';
import { CategoryShowcase } from '../components/CategoryShowcase';
import { ClosingCta } from '../components/ClosingCta';
import { FeaturedProducts } from '../components/FeaturedProducts';
import { GalleryTeaser } from '../components/GalleryTeaser';
import { Hero } from '../components/Hero';
import { ProcessSection } from '../components/ProcessSection';
import { ValuesSection } from '../components/ValuesSection';

export function HomePage() {
  const { t } = useTranslation();
  return (
    <>
      <PageMeta title={t('home.meta.title')} description={t('home.meta.description')} />
      <Hero />
      <CategoryShowcase />
      <FeaturedProducts />
      <ValuesSection />
      <ProcessSection />
      <GalleryTeaser />
      <ClosingCta />
    </>
  );
}

import { useTranslation } from 'react-i18next';
import { PageMeta } from '@/lib/seo/PageMeta';
import { CategoryShowcase } from '../components/CategoryShowcase';
import { ContactStrip } from '../components/ContactStrip';
import { FeaturedProducts } from '../components/FeaturedProducts';
import { GalleryTeaser } from '../components/GalleryTeaser';
import { Hero } from '../components/Hero';
import { ShippingWeek } from '../components/ShippingWeek';

export function HomePage() {
  const { t } = useTranslation();
  return (
    <>
      <PageMeta title={t('home.meta.title')} description={t('home.meta.description')} />
      <Hero />
      <CategoryShowcase />
      <FeaturedProducts />
      <ShippingWeek />
      <GalleryTeaser />
      <ContactStrip />
    </>
  );
}

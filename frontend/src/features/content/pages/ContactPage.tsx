import { CalendarDays, Clock, Mail } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Section, SectionHeading } from '@/components/ui/Section';
import { shopConfig } from '@/config/shop';
import { PageMeta } from '@/lib/seo/PageMeta';
import { ContactForm } from '../components/ContactForm';

export function ContactPage() {
  const { t } = useTranslation();
  const email = shopConfig.email;

  const info = [
    {
      Icon: Mail,
      title: t('contact.info.emailTitle'),
      value: (
        <a href={`mailto:${email}`} className="underline-offset-4 hover:underline">
          {email}
        </a>
      ),
    },
    { Icon: Clock, title: t('contact.info.responseTitle'), value: t('contact.info.response') },
    { Icon: CalendarDays, title: t('contact.info.shippingTitle'), value: t('contact.info.shipping') },
  ];

  return (
    <>
      <PageMeta title={t('contact.meta.title')} description={t('contact.meta.description')} />
      <Section spacing="compact">
        <div className="grid gap-16 lg:grid-cols-12 lg:gap-12">
          <div className="lg:col-span-5">
            <SectionHeading as="h1" title={t('contact.title')} lead={t('contact.lead')} />
            <dl className="mt-12 space-y-6 border-t border-line pt-10">
              {info.map(({ Icon, title, value }) => (
                <div key={title} className="flex gap-4">
                  <Icon aria-hidden className="mt-0.5 size-5 shrink-0 text-brand-700" strokeWidth={1.5} />
                  <div>
                    <dt className="text-sm text-muted">{title}</dt>
                    <dd className="mt-1 text-lg">{value}</dd>
                  </div>
                </div>
              ))}
            </dl>
          </div>
          <div className="lg:col-span-6 lg:col-start-7">
            <div className="relative rounded-card bg-surface p-6 shadow-soft ring-1 ring-line sm:p-10">
              <ContactForm />
            </div>
          </div>
        </div>
      </Section>
    </>
  );
}

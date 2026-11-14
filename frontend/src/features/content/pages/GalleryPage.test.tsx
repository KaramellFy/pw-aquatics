import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mockApi, problem } from '@/test/mockApi';
import { renderWithProviders } from '@/test/render';
import { ShippingPauseBanner } from '../components/ShippingPauseBanner';
import { GalleryPage } from './GalleryPage';

afterEach(() => vi.unstubAllGlobals());

const photo = {
  id: 'g1',
  title: 'Blue Dream on moss',
  caption: 'Bred in our own tanks.',
  image: {
    url: '/media/gallery/g1/x/800.webp',
    srcSet: '/media/gallery/g1/x/400.webp 400w',
    alt: 'Blue shrimp grazing on moss',
    width: 1600,
    height: 1200,
  },
};

describe('GalleryPage', () => {
  it('shows the photos the owner has published', async () => {
    mockApi({ '/gallery': { items: [photo], page: 0, size: 24, totalItems: 1, totalPages: 1 } });
    renderWithProviders(<GalleryPage />);

    expect(
      await screen.findByRole('button', { name: 'Enlarge image: Blue Dream on moss' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Blue Dream on moss')).toBeInTheDocument();
    expect(screen.queryByText('Neocaridina davidi “Blue Dream”')).not.toBeInTheDocument();
  });

  it('shows illustrations until photos are published, and when they cannot be loaded', async () => {
    mockApi({ '/gallery': { items: [], page: 0, size: 24, totalItems: 0, totalPages: 0 } });
    const first = renderWithProviders(<GalleryPage />);
    expect(await screen.findByText('Neocaridina davidi “Blue Dream”')).toBeInTheDocument();
    first.unmount();

    mockApi({ '/gallery': problem(500, 'INTERNAL_ERROR') });
    renderWithProviders(<GalleryPage />);
    expect(await screen.findByText('Neocaridina davidi “Blue Dream”')).toBeInTheDocument();
  });
});

describe('ShippingPauseBanner', () => {
  it('appears only while the server reports a pause', async () => {
    mockApi({ '/shop-info': { shippingPause: { message: 'It is too hot.', until: '2026-11-20' } } });
    const first = renderWithProviders(<ShippingPauseBanner />);
    const notice = await screen.findByRole('complementary', { name: 'Shipping notice' });
    expect(notice).toHaveTextContent('We ship live animals again after 20 November 2026.');
    expect(notice).toHaveTextContent('It is too hot.');
    first.unmount();

    mockApi({ '/shop-info': { shippingPause: null } });
    renderWithProviders(<ShippingPauseBanner />);
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument();
  });
});

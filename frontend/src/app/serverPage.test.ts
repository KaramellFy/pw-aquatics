import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import { queryKeys } from '@/lib/api/queryKeys';
import { dropStructuredDataOnLeave, takeOverServerPage, whenRouterReady } from './serverPage';

function serverPage(data: string): Document {
  const doc = document.implementation.createHTMLDocument('');
  doc.head.innerHTML =
    '<title data-page>Blue Dream – PW Aquatics</title><link data-page rel="canonical" href="https://x/products/blue-dream">' +
    '<link rel="stylesheet" href="/assets/index.css">' +
    '<script data-page type="application/ld+json">{"@type":"Product"}</script>';
  doc.body.innerHTML = `<div id="root"><h1>Blue Dream</h1></div><script type="application/json" id="page-data">${data}</script>`;
  return doc;
}

describe('a page the backend filled in', () => {
  it('hands its data to the cache and its head over to the app', () => {
    const doc = serverPage(
      '{"/products/blue-dream":{"slug":"blue-dream","name":"Blue \\u003cDream\\u003e"}}',
    );
    const client = new QueryClient();

    takeOverServerPage(client, doc);

    expect(client.getQueryData(queryKeys.product('blue-dream'))).toEqual({
      slug: 'blue-dream',
      name: 'Blue <Dream>',
    });
    expect(doc.head.querySelectorAll('[data-page]:not(script)')).toHaveLength(0);
    expect(doc.head.querySelector('link[rel="stylesheet"]')).not.toBeNull();
    // Structured data is only written by the backend; it stays for this page.
    expect(doc.head.querySelector('script[type="application/ld+json"]')).not.toBeNull();
    expect(doc.getElementById('page-data')).toBeNull();
    // The text stays until the app renders over it.
    expect(doc.getElementById('root')?.textContent).toBe('Blue Dream');
  });

  it('ignores data it cannot read', () => {
    const client = new QueryClient();
    expect(() => takeOverServerPage(client, serverPage('{broken'))).not.toThrow();
    expect(client.getQueryCache().getAll()).toHaveLength(0);
  });

  it('drops its structured data once the app shows another page', () => {
    const doc = serverPage('{}');
    let listener: ((state: { location: { pathname: string } }) => void) | undefined;
    let unsubscribed = false;
    const router = {
      state: { location: { pathname: '/products/blue-dream' } },
      subscribe: (next: (state: { location: { pathname: string } }) => void) => {
        listener = next;
        return () => {
          unsubscribed = true;
        };
      },
    };
    takeOverServerPage(new QueryClient(), doc);
    dropStructuredDataOnLeave(router, doc);

    // Same page (e.g. another pack size chosen): the data still describes it.
    listener?.({ location: { pathname: '/products/blue-dream' } });
    expect(doc.head.querySelector('script[type="application/ld+json"]')).not.toBeNull();

    listener?.({ location: { pathname: '/shop' } });
    expect(doc.head.querySelector('script[type="application/ld+json"]')).toBeNull();
    expect(unsubscribed).toBe(true);
  });

  it('waits for the router to load the first page', async () => {
    let listener: ((state: { initialized: boolean }) => void) | undefined;
    let unsubscribed = false;
    const router = {
      state: { initialized: false },
      subscribe: (next: (state: { initialized: boolean }) => void) => {
        listener = next;
        return () => {
          unsubscribed = true;
        };
      },
    };
    let ready = false;
    const waiting = whenRouterReady(router).then(() => {
      ready = true;
    });
    listener?.({ initialized: false });
    await Promise.resolve();
    expect(ready).toBe(false);
    listener?.({ initialized: true });
    await waiting;
    expect(ready).toBe(true);
    expect(unsubscribed).toBe(true);
  });
});

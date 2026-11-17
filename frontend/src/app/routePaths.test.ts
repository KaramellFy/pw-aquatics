import type { RouteObject } from 'react-router';
import { describe, expect, it } from 'vitest';
import { ROUTE_PATHS } from './routePaths';
import { routes } from './routes';

/** The full address of every page in the route tree; the top-level catch-all is "not found". */
function pagePaths(tree: RouteObject[], parent = ''): string[] {
  return tree.flatMap((route) => {
    const own = route.path === undefined ? parent : `${parent}/${route.path}`;
    if (route.children) return pagePaths(route.children, own);
    return route.index ? [parent || '/'] : [own || '/'];
  });
}

describe('the route list the backend gets', () => {
  it('names exactly the pages of the route tree', () => {
    const tree = pagePaths(routes).filter((path) => path !== '/*');
    expect([...ROUTE_PATHS].sort()).toEqual([...new Set(tree)].sort());
  });
});

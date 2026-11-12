import { describe, expect, it } from 'vitest';
import { labelEvery, niceTicks } from './scale';

describe('chart scales', () => {
  it('picks round ticks that reach the largest value', () => {
    expect(niceTicks(14480)).toEqual([0, 5000, 10000, 15000]);
    expect(niceTicks(7, { integer: true })).toEqual([0, 2, 4, 6, 8]);
    expect(niceTicks(3, { integer: true })).toEqual([0, 1, 2, 3]);
    expect(niceTicks(100)).toEqual([0, 25, 50, 75, 100]);
  });

  it('still draws an axis when everything is zero', () => {
    expect(niceTicks(0, { integer: true })).toEqual([0, 1]);
    expect(niceTicks(0, { emptyTop: 1000 })).toEqual([0, 250, 500, 750, 1000]);
  });

  it('thins out axis labels on narrow charts', () => {
    expect(labelEvery(7, 600)).toBe(1);
    expect(labelEvery(31, 600)).toBe(4);
    expect(labelEvery(31, 280)).toBe(7);
  });
});

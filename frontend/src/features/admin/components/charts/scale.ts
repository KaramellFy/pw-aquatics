/**
 * Axis helpers for the admin charts. These only map values the API sent to pixels and pick
 * round tick values; they never derive business figures.
 */

/**
 * Round tick values from zero to at least `max`: steps of 1, 2, 2.5 or 5 × 10ⁿ, about
 * `target` of them. With `integer`, steps are whole numbers (for counts).
 */
export function niceTicks(max: number, { target = 4, integer = false, emptyTop = 1 } = {}): number[] {
  const top = max > 0 ? max : emptyTop;
  const rough = top / target;
  const power = 10 ** Math.floor(Math.log10(rough));
  const multipliers = integer ? [1, 2, 5, 10] : [1, 2, 2.5, 5, 10];
  let step = multipliers.map((m) => m * power).find((candidate) => candidate >= rough) ?? 10 * power;
  if (integer) step = Math.max(1, Math.round(step));
  const ticks: number[] = [];
  for (let value = 0; value < top + step; value += step) {
    ticks.push(value);
    if (value >= top) break;
  }
  return ticks;
}

/** Which of `count` axis labels to print so they don't collide; the last one is always shown. */
export function labelEvery(count: number, plotWidth: number, minLabelWidth = 56): number {
  const fits = Math.max(1, Math.floor(plotWidth / minLabelWidth));
  return Math.max(1, Math.ceil(count / fits));
}

/** SVG path of a column with rounded top corners and a square base. */
export function columnPath(x: number, y: number, width: number, height: number, radius = 4): string {
  const r = Math.max(0, Math.min(radius, height, width / 2));
  return [
    `M${x},${y + height}`,
    `V${y + r}`,
    `A${r},${r} 0 0 1 ${x + r},${y}`,
    `H${x + width - r}`,
    `A${r},${r} 0 0 1 ${x + width},${y + r}`,
    `V${y + height}`,
    'Z',
  ].join('');
}

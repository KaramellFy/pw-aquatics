import { encode } from 'uqr';

/**
 * A QR code drawn as SVG in the browser: the link for the authenticator app contains the secret
 * key, so it is never sent to a QR service. Dark modules on white with a quiet zone of four
 * modules, so phone cameras read it in any theme.
 */
export function QrCode({ value, label, className }: { value: string; label: string; className?: string }) {
  const { data, size } = encode(value, { ecc: 'M', border: 4 });
  // One path for all dark modules keeps the SVG small.
  const path = data.flatMap((row, y) => row.map((dark, x) => (dark ? `M${x} ${y}h1v1h-1z` : ''))).join('');
  return (
    <svg
      role="img"
      aria-label={label}
      viewBox={`0 0 ${size} ${size}`}
      shapeRendering="crispEdges"
      className={className}
    >
      <rect width={size} height={size} className="fill-white" />
      <path d={path} className="fill-ink" />
    </svg>
  );
}

import type { components } from './schema';

/**
 * API types. Response and request shapes come from the generated OpenAPI types
 * (`npm run generate:api` → schema.d.ts); only cross-cutting shapes the spec
 * does not describe (Problem Details) are written by hand here.
 */
export type Schemas = components['schemas'];

/** Money in minor units (cents). Always calculated by the server — never do arithmetic on it in the UI. */
export type Money = Schemas['Money'];

/** A single field error inside a Problem Details response. */
export interface FieldError {
  field: string;
  code: string;
  message: string;
}

/** RFC 9457 Problem Details as returned by the backend (see docs/API.md §1.4). */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  code?: string;
  instance?: string;
  traceId?: string;
  errors?: FieldError[];
}

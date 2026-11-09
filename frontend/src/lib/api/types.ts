/**
 * Shared API types.
 *
 * TEMPORARY: once the backend exists, types are generated from its OpenAPI spec
 * (`npm run generate:api` → schema.d.ts). Only keep hand-written types here for
 * cross-cutting shapes that the spec does not describe (e.g. Problem Details).
 */

/** Money in minor units (cents). Always calculated by the server — never do arithmetic on it in the UI. */
export interface Money {
  amount: number;
  currency: string;
}

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

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

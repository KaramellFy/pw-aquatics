import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiError } from '@/lib/api/client';

/**
 * Shows server-side validation errors next to the matching form fields.
 * The server stays authoritative; client-side rules only give faster feedback.
 *
 * @returns true if at least one error was attached to a field
 */
export function applyServerFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): boolean {
  if (!(error instanceof ApiError)) return false;
  let applied = false;
  for (const fieldError of error.fieldErrors) {
    const field = fields.find((name) => name === fieldError.field);
    if (field) {
      setError(field, { message: fieldError.message });
      applied = true;
    }
  }
  return applied;
}

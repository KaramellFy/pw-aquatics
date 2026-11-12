import { useMutation, useQuery, useQueryClient, type QueryKey } from '@tanstack/react-query';
import { api } from '@/lib/api/client';
import { queryKeys } from '@/lib/api/queryKeys';
import type { Schemas } from '@/lib/api/types';

export type AdminCategory = Schemas['AdminCategoryView'];
export type AdminSpecies = Schemas['AdminSpeciesView'];
export type AdminColour = Schemas['AdminColourView'];
export type AdminGrade = Schemas['AdminGradeView'];
export type CategoryInput = Schemas['CategoryRequest'];
export type SpeciesInput = Schemas['SpeciesRequest'];
export type ColourInput = Schemas['ColourRequest'];
export type GradeInput = Schemas['GradeRequest'];

function useList<T>(key: QueryKey, path: string) {
  return useQuery({ queryKey: key, queryFn: ({ signal }) => api.get<T[]>(path, signal) });
}

export const useAdminCategories = () =>
  useList<AdminCategory>(queryKeys.adminCategories, '/admin/categories');
export const useAdminSpecies = () => useList<AdminSpecies>(queryKeys.adminSpecies, '/admin/species');
export const useAdminColours = () => useList<AdminColour>(queryKeys.adminColours, '/admin/colours');
export const useAdminGrades = () => useList<AdminGrade>(queryKeys.adminGrades, '/admin/grades');

export function useAdminSpeciesDetail(id: string) {
  return useQuery({
    queryKey: [...queryKeys.adminSpecies, id],
    queryFn: ({ signal }) => api.get<AdminSpecies>(`/admin/species/${encodeURIComponent(id)}`, signal),
    enabled: id !== '',
  });
}

/**
 * Create (no id) or update (with id) a category, species, colour or grade. Afterwards the
 * admin list and the public catalog are refreshed.
 */
function useSave<TInput, TView>(key: QueryKey, path: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: TInput }) =>
      id ? api.put<TView>(`${path}/${encodeURIComponent(id)}`, input) : api.post<TView>(path, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: key });
      void queryClient.invalidateQueries({ queryKey: ['catalog'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
    },
  });
}

function useDelete(key: QueryKey, path: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api.delete<void>(`${path}/${encodeURIComponent(id)}`),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: key });
      void queryClient.invalidateQueries({ queryKey: ['catalog'] });
    },
  });
}

export const useSaveCategory = () =>
  useSave<CategoryInput, AdminCategory>(queryKeys.adminCategories, '/admin/categories');
export const useDeleteCategory = () => useDelete(queryKeys.adminCategories, '/admin/categories');
export const useSaveSpecies = () =>
  useSave<SpeciesInput, AdminSpecies>(queryKeys.adminSpecies, '/admin/species');
export const useDeleteSpecies = () => useDelete(queryKeys.adminSpecies, '/admin/species');
export const useSaveColour = () =>
  useSave<ColourInput, AdminColour>(queryKeys.adminColours, '/admin/colours');
export const useDeleteColour = () => useDelete(queryKeys.adminColours, '/admin/colours');
export const useSaveGrade = () => useSave<GradeInput, AdminGrade>(queryKeys.adminGrades, '/admin/grades');
export const useDeleteGrade = () => useDelete(queryKeys.adminGrades, '/admin/grades');

/** Categories in tree order, each with its depth (for indented lists and selects). */
export function categoryTree(categories: AdminCategory[]): { category: AdminCategory; depth: number }[] {
  const children = new Map<string | null, AdminCategory[]>();
  for (const category of categories) {
    const parent = category.parentId ?? null;
    children.set(parent, [...(children.get(parent) ?? []), category]);
  }
  const ordered: { category: AdminCategory; depth: number }[] = [];
  const visit = (parent: string | null, depth: number) => {
    for (const category of children.get(parent) ?? []) {
      ordered.push({ category, depth });
      visit(category.id, depth + 1);
    }
  };
  visit(null, 0);
  return ordered;
}

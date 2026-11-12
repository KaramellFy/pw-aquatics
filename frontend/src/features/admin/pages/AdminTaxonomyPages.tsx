import { useTranslation } from 'react-i18next';
import {
  categoryTree,
  useAdminCategories,
  useAdminColours,
  useAdminGrades,
  useAdminSpecies,
  useDeleteCategory,
  useDeleteColour,
  useDeleteGrade,
  useDeleteSpecies,
  type AdminCategory,
  type AdminColour,
  type AdminGrade,
  type AdminSpecies,
} from '../api/taxonomy';
import { Pill } from '../components/StatusBadge';
import { TaxonomyListPage } from '../components/TaxonomyListPage';
import { CategoryForm, ColourForm, GradeForm } from '../components/TaxonomyForms';

export function AdminCategoriesPage() {
  const { t } = useTranslation();
  const categories = useAdminCategories();
  const remove = useDeleteCategory();
  const depth = new Map(categoryTree(categories.data ?? []).map((entry) => [entry.category.id, entry.depth]));
  const ordered = categoryTree(categories.data ?? []).map((entry) => entry.category);

  return (
    <TaxonomyListPage<AdminCategory>
      title={t('admin.categories.title')}
      lead={t('admin.categories.lead')}
      addLabel={t('admin.categories.add')}
      emptyText={t('admin.categories.empty')}
      query={{ ...categories, data: categories.data ? ordered : undefined }}
      nameOf={(category) => category.name}
      columns={[
        {
          header: t('admin.categories.columns.name'),
          cell: (category) => (
            <span
              className="font-medium"
              style={{ paddingInlineStart: `${(depth.get(category.id) ?? 0) * 1.5}rem` }}
            >
              {category.name}
            </span>
          ),
        },
        {
          header: t('admin.categories.columns.slug'),
          cell: (category) => <code className="text-sm">{category.slug}</code>,
        },
        {
          header: t('admin.categories.columns.products'),
          cell: (category) => category.productCount,
          className: 'text-right tabular-nums',
        },
        {
          header: t('admin.categories.columns.shop'),
          cell: (category) => (
            <Pill tone={category.active ? 'ok' : 'neutral'}>
              {category.active ? t('admin.categories.shown') : t('admin.categories.hidden')}
            </Pill>
          ),
        },
      ]}
      form={{
        addTitle: t('admin.categories.addTitle'),
        editTitle: t('admin.categories.editTitle'),
        render: (category, close) => <CategoryForm category={category} onDone={close} />,
      }}
      remove={remove}
    />
  );
}

export function AdminColoursPage() {
  const { t } = useTranslation();
  const colours = useAdminColours();
  const remove = useDeleteColour();
  return (
    <TaxonomyListPage<AdminColour>
      title={t('admin.colours.title')}
      lead={t('admin.colours.lead')}
      addLabel={t('admin.colours.add')}
      emptyText={t('admin.colours.empty')}
      query={colours}
      nameOf={(colour) => colour.name}
      columns={[
        {
          header: t('admin.colours.columns.colour'),
          cell: (colour) => (
            <span className="inline-flex items-center gap-3 font-medium">
              <span
                aria-hidden
                className="size-5 rounded-full border border-ink/15"
                style={colour.hexCode ? { backgroundColor: colour.hexCode } : undefined}
              />
              {colour.name}
              {colour.hexCode && <code className="text-xs text-muted">{colour.hexCode}</code>}
            </span>
          ),
        },
        {
          header: t('admin.colours.columns.slug'),
          cell: (colour) => <code className="text-sm">{colour.slug}</code>,
        },
        {
          header: t('admin.colours.columns.products'),
          cell: (colour) => colour.productCount,
          className: 'text-right tabular-nums',
        },
      ]}
      form={{
        addTitle: t('admin.colours.addTitle'),
        editTitle: t('admin.colours.editTitle'),
        render: (colour, close) => <ColourForm colour={colour} onDone={close} />,
      }}
      remove={remove}
    />
  );
}

export function AdminGradesPage() {
  const { t } = useTranslation();
  const grades = useAdminGrades();
  const remove = useDeleteGrade();
  return (
    <TaxonomyListPage<AdminGrade>
      title={t('admin.grades.title')}
      lead={t('admin.grades.lead')}
      addLabel={t('admin.grades.add')}
      emptyText={t('admin.grades.empty')}
      query={grades}
      nameOf={(grade) => grade.name}
      columns={[
        {
          header: t('admin.grades.columns.name'),
          cell: (grade) => (
            <>
              <span className="font-medium">{grade.name}</span>
              {grade.scale && <span className="block text-xs text-muted">{grade.scale}</span>}
            </>
          ),
        },
        {
          header: t('admin.grades.columns.rank'),
          cell: (grade) => grade.rank,
          className: 'text-right tabular-nums',
        },
        {
          header: t('admin.grades.columns.products'),
          cell: (grade) => grade.productCount,
          className: 'text-right tabular-nums',
        },
      ]}
      form={{
        addTitle: t('admin.grades.addTitle'),
        editTitle: t('admin.grades.editTitle'),
        render: (grade, close) => <GradeForm grade={grade} onDone={close} />,
      }}
      remove={remove}
    />
  );
}

export function AdminSpeciesPage() {
  const { t } = useTranslation();
  const species = useAdminSpecies();
  const remove = useDeleteSpecies();
  return (
    <TaxonomyListPage<AdminSpecies>
      title={t('admin.species.title')}
      lead={t('admin.species.lead')}
      addLabel={t('admin.species.add')}
      emptyText={t('admin.species.empty')}
      query={species}
      nameOf={(item) => item.scientificName}
      editHref={(item) => (item ? `/admin/species/${item.id}` : '/admin/species/new')}
      columns={[
        {
          header: t('admin.species.columns.name'),
          cell: (item) => <span className="scientific font-medium">{item.scientificName}</span>,
        },
        {
          header: t('admin.species.columns.commonName'),
          cell: (item) => item.commonName ?? t('admin.common.none'),
        },
        {
          header: t('admin.species.columns.products'),
          cell: (item) => item.productCount,
          className: 'text-right tabular-nums',
        },
        {
          header: t('admin.species.columns.trade'),
          cell: (item) => (
            <Pill tone={item.tradeRestricted ? 'warning' : 'ok'}>
              {item.tradeRestricted ? t('admin.species.restricted') : t('admin.species.allowed')}
            </Pill>
          ),
        },
      ]}
      remove={remove}
    />
  );
}

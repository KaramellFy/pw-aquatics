package com.pwaquatics.shop.pages.application;

import com.pwaquatics.shop.catalog.application.CatalogQueryService;
import com.pwaquatics.shop.catalog.application.ProductSearch;
import com.pwaquatics.shop.catalog.application.view.CategoryNodeView;
import com.pwaquatics.shop.catalog.application.view.CategoryRef;
import com.pwaquatics.shop.catalog.application.view.ProductDetailView;
import com.pwaquatics.shop.catalog.application.view.ProductSummaryView;
import com.pwaquatics.shop.catalog.application.view.SpeciesView;
import com.pwaquatics.shop.catalog.application.view.VariantView;
import com.pwaquatics.shop.catalog.domain.Availability;
import com.pwaquatics.shop.catalog.domain.AvailabilityFilter;
import com.pwaquatics.shop.catalog.domain.ProductSort;
import com.pwaquatics.shop.common.api.PageResponse;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.common.text.ContentLanguage;
import com.pwaquatics.shop.common.web.SiteLinks;
import com.pwaquatics.shop.content.application.GalleryService;
import com.pwaquatics.shop.content.application.view.GalleryImageView;
import com.pwaquatics.shop.media.application.ImageView;
import com.pwaquatics.shop.pages.application.PageWriter.Crumb;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Fills in the public pages of the shop for the first page load (docs/adr/013-pages-filled-in-by-the-backend.md):
 * title, description, language links and structured data in the head, the page's main text in
 * the app's root element, and the API responses the app would request first. The app replaces
 * the text when it starts, so the server's text only has to be right, not pretty.
 *
 * <p>Filled in are the home page, shop and category pages, products, species, gallery, about
 * and contact, in English and under "/de" in German. Unknown products, species and categories
 * answer 404. Every other path gets the template with only the language set.
 */
@Service
public class PageService {

    /** The app's page size for product lists (frontend shopFilters.ts PAGE_SIZE). */
    static final int SHOP_PAGE_SIZE = 24;
    /** The species page's product list (frontend SpeciesPage.tsx). */
    static final int SPECIES_PAGE_SIZE = 8;
    /** The home page's featured products (frontend Hero.tsx FEATURED_QUERY). */
    static final int FEATURED_SIZE = 4;
    static final int GALLERY_PAGE_SIZE = 24;

    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,199}");
    private static final int DESCRIPTION_LENGTH = 160;
    private static final Map<Availability, String> SCHEMA_AVAILABILITY = Map.of(
            Availability.IN_STOCK, "https://schema.org/InStock",
            Availability.LOW_STOCK, "https://schema.org/LimitedAvailability",
            Availability.COMING_SOON, "https://schema.org/PreOrder",
            Availability.OUT_OF_STOCK, "https://schema.org/OutOfStock");

    private final ShellSource shells;
    private final CatalogQueryService catalog;
    private final GalleryService gallery;
    private final PageWriter writer;
    private final SiteLinks links;
    private final ObjectMapper json;

    public PageService(
            ShellSource shells,
            CatalogQueryService catalog,
            GalleryService gallery,
            PageWriter writer,
            SiteLinks links,
            ObjectMapper json) {
        this.shells = shells;
        this.catalog = catalog;
        this.gallery = gallery;
        this.writer = writer;
        this.links = links;
        this.json = json;
    }

    /**
     * @param path the requested page, e.g. "/de/products/blue-dream" (no query)
     * @throws ShellSource.ShellUnavailableException if there is no template to fill in
     */
    public RenderedPage render(String path) {
        PageShell shell = shells.current();
        String normalised = normalise(path);
        boolean german = normalised.equals("/de") || normalised.startsWith("/de/");
        ContentLanguage language = german ? ContentLanguage.DE : ContentLanguage.EN;
        String inLanguage = german ? normalise(normalised.substring(3)) : normalised;
        PageContext page = new PageContext(language, shell.texts());

        // Catalog texts follow the request's language (ContentLanguage.current()); here the link decides.
        LocaleContext previous = LocaleContextHolder.getLocaleContext();
        LocaleContextHolder.setLocale(page.locale());
        try {
            FilledPage filled = route(inLanguage, page);
            String head = filled.head() == null ? null : writer.head(filled.head(), page);
            String data = filled.data().isEmpty() ? null : json.writeValueAsString(filled.data());
            return new RenderedPage(filled.status(), shell.render(page.code(), head, filled.body(), data));
        } finally {
            LocaleContextHolder.setLocaleContext(previous);
        }
    }

    private FilledPage route(String path, PageContext page) {
        List<String> segments = "/".equals(path) ? List.of() : List.of(path.substring(1).split("/"));
        try {
            return switch (segments.size()) {
                case 0 -> home(page);
                case 1 -> switch (segments.getFirst()) {
                    case "shop" -> shop(page, null);
                    case "gallery" -> gallery(page);
                    case "about", "contact" -> intro(page, segments.getFirst());
                    default -> FilledPage.plain();
                };
                case 2 -> switch (segments.getFirst()) {
                    case "shop" -> shop(page, slug(segments.get(1)));
                    case "products" -> product(page, slug(segments.get(1)));
                    case "species" -> species(page, slug(segments.get(1)));
                    default -> FilledPage.plain();
                };
                default -> FilledPage.plain();
            };
        } catch (NotFoundException e) {
            return notFound(page);
        }
    }

    private FilledPage home(PageContext page) {
        Page<ProductSummaryView> featured = catalog.searchProducts(new ProductSearch(
                null, null, null, null, null, null, null, AvailabilityFilter.IN_STOCK, true,
                ProductSort.RELEVANCE, 0, FEATURED_SIZE));
        StringBuilder body = open(page);
        writer.heading(body, page.text("pages.home.heading"), page.text("pages.home.lead"));
        writer.products(body, page, featured.getContent());
        return new FilledPage(
                200,
                PageHead.of(page.text("pages.home.title"), page.text("pages.home.description"), "/"),
                close(body),
                Map.of("/products?featured=true&availability=IN_STOCK&size=" + FEATURED_SIZE, PageResponse.of(featured)));
    }

    private FilledPage intro(PageContext page, String name) {
        StringBuilder body = open(page);
        writer.heading(body, page.text("pages." + name + ".heading"), page.text("pages." + name + ".lead"));
        return new FilledPage(
                200,
                PageHead.of(page.text("pages." + name + ".title"), page.text("pages." + name + ".description"), "/" + name),
                close(body),
                Map.of());
    }

    private FilledPage shop(PageContext page, String categorySlug) {
        List<CategoryNodeView> tree = catalog.categoryTree();
        List<CategoryNodeView> path = categorySlug == null ? List.of() : pathTo(tree, categorySlug);
        if (categorySlug != null && path.isEmpty()) {
            throw new NotFoundException("Category not found.");
        }
        CategoryNodeView category = path.isEmpty() ? null : path.getLast();
        Page<ProductSummaryView> products = catalog.searchProducts(new ProductSearch(
                null, categorySlug, null, null, null, null, null, null, false, ProductSort.RELEVANCE, 0, SHOP_PAGE_SIZE));

        String brand = page.text("brand");
        String title = category == null ? page.text("pages.shop.title") : category.name() + " – " + brand;
        String description = category != null && category.description() != null
                ? category.description() : page.text("pages.shop.description");

        StringBuilder body = open(page);
        List<Crumb> crumbs = new ArrayList<>();
        crumbs.add(new Crumb(page.text("nav.shop"), page.link("/shop")));
        path.stream().limit(Math.max(0, path.size() - 1))
                .forEach(node -> crumbs.add(new Crumb(node.name(), page.link("/shop/" + node.slug()))));
        if (category != null) {
            writer.breadcrumbs(body, crumbs);
        }
        writer.heading(body,
                category == null ? page.text("pages.shop.heading") : category.name(),
                category != null && category.description() != null ? category.description() : page.text("pages.shop.lead"));
        List<CategoryNodeView> below = category == null ? tree : category.children();
        if (!below.isEmpty()) {
            body.append("<ul class=\"page-preview-categories\">");
            for (CategoryNodeView child : below) {
                body.append("<li>");
                writer.link(body, page.link("/shop/" + child.slug()), child.name());
                body.append("</li>");
            }
            body.append("</ul>");
        }
        writer.products(body, page, products.getContent());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("/categories", tree);
        data.put("/products?" + (categorySlug == null ? "" : "category=" + categorySlug + "&") + "size=" + SHOP_PAGE_SIZE,
                PageResponse.of(products));
        String own = categorySlug == null ? "/shop" : "/shop/" + categorySlug;
        return new FilledPage(200, PageHead.of(title, description, own), close(body), data);
    }

    private FilledPage product(PageContext page, String slug) {
        ProductDetailView product = catalog.product(slug);
        String own = "/products/" + slug;
        ImageView photo = product.images().isEmpty() ? null : product.images().getFirst();
        String description = product.seo().description() != null ? product.seo().description() : product.shortDescription();

        StringBuilder body = open(page);
        List<Crumb> crumbs = new ArrayList<>();
        crumbs.add(new Crumb(page.text("nav.shop"), page.link("/shop")));
        for (CategoryRef category : product.categoryPath()) {
            crumbs.add(new Crumb(category.name(), page.link("/shop/" + category.slug())));
        }
        writer.breadcrumbs(body, crumbs);
        body.append("<h1>").append(Html.text(product.name())).append("</h1>");
        if (product.species() != null) {
            body.append("<p><i>").append(Html.text(product.species().scientificName())).append("</i></p>");
        }
        if (photo != null) {
            writer.image(body, photo, "(min-width: 1024px) 50vw, 100vw");
        }
        writer.paragraph(body, product.shortDescription());
        if (!product.variants().isEmpty()) {
            body.append("<ul class=\"page-preview-variants\">");
            for (VariantView variant : product.variants()) {
                body.append("<li>").append(Html.text(variant.name())).append(": ")
                        .append(Html.text(page.money(variant.price()))).append("</li>");
            }
            body.append("</ul>");
        }
        writer.paragraph(body, page.text("product.availability." + product.availability()));
        if (product.descriptionHtml() != null) {
            // Sanitised server-side when rendered from Markdown (docs/API.md §2.2).
            body.append("<div class=\"page-preview-text\">").append(product.descriptionHtml()).append("</div>");
        }

        PageHead head = new PageHead(
                product.seo().title() + " – " + page.text("brand"),
                description,
                own,
                product.languages(),
                false,
                "product",
                photo == null ? null : photo.url(),
                productData(product, page));
        return new FilledPage(200, head, close(body), Map.of("/products/" + slug, product));
    }

    private FilledPage species(PageContext page, String slug) {
        SpeciesView species = catalog.species(slug);
        Page<ProductSummaryView> products = catalog.searchProducts(new ProductSearch(
                null, null, List.of(slug), null, null, null, null, null, false, ProductSort.RELEVANCE, 0, SPECIES_PAGE_SIZE));
        String name = species.commonName() != null ? species.commonName() : species.scientificName();
        String plain = Html.plainText(species.descriptionHtml());
        String description = plain == null || plain.isEmpty()
                ? null : plain.substring(0, Math.min(plain.length(), DESCRIPTION_LENGTH));

        StringBuilder body = open(page);
        body.append("<h1>").append(Html.text(name)).append("</h1>");
        body.append("<p><i>").append(Html.text(species.scientificName())).append("</i></p>");
        if (species.descriptionHtml() != null) {
            body.append("<div class=\"page-preview-text\">").append(species.descriptionHtml()).append("</div>");
        }
        if (!products.isEmpty()) {
            body.append("<h2>").append(Html.text(page.text("species.available"))).append("</h2>");
            writer.products(body, page, products.getContent());
        }

        PageHead head = new PageHead(
                name + " (" + species.scientificName() + ") – " + page.text("brand"),
                description,
                "/species/" + slug,
                species.languages(),
                false,
                "website",
                null,
                null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("/species/" + slug, species);
        data.put("/products?species=" + slug + "&size=" + SPECIES_PAGE_SIZE, PageResponse.of(products));
        return new FilledPage(200, head, close(body), data);
    }

    private FilledPage gallery(PageContext page) {
        Page<GalleryImageView> photos = gallery.published(0, GALLERY_PAGE_SIZE);
        StringBuilder body = open(page);
        writer.heading(body, page.text("pages.gallery.heading"), page.text("pages.gallery.lead"));
        for (GalleryImageView photo : photos.getContent()) {
            body.append("<figure>");
            writer.image(body, photo.image(), "(min-width: 768px) 33vw, 100vw");
            String caption = Stream.of(photo.title(), photo.caption())
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining(" – "));
            if (!caption.isEmpty()) {
                body.append("<figcaption>").append(Html.text(caption)).append("</figcaption>");
            }
            body.append("</figure>");
        }
        return new FilledPage(
                200,
                PageHead.of(page.text("pages.gallery.title"), page.text("pages.gallery.description"), "/gallery"),
                close(body),
                Map.of());
    }

    private FilledPage notFound(PageContext page) {
        StringBuilder body = open(page);
        writer.heading(body, page.text("notFound.heading"), page.text("notFound.text"));
        body.append("<p>");
        writer.link(body, page.link("/"), page.text("backToHome"));
        body.append("</p>");
        return new FilledPage(404, PageHead.notIndexed(page.text("notFound.title")), close(body), Map.of());
    }

    /** schema.org Product, as the app's product page writes it (step 4 adds one offer per pack size). */
    private Map<String, Object> productData(ProductDetailView product, PageContext page) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("@context", "https://schema.org");
        data.put("@type", "Product");
        data.put("name", product.species() == null
                ? product.name() : product.name() + " (" + product.species().scientificName() + ")");
        String description = product.seo().description() != null ? product.seo().description() : product.shortDescription();
        if (description != null) {
            data.put("description", description);
        }
        if (!product.variants().isEmpty()) {
            data.put("sku", product.variants().getFirst().sku());
        }
        data.put("category", product.categoryPath().stream().map(CategoryRef::name).collect(Collectors.joining(" > ")));
        data.put("image", product.images().stream().map(image -> links.file(image.url())).toList());
        data.put("url", links.page(page.code(), "/products/" + product.slug()));
        List<Money> prices = product.variants().stream().map(VariantView::price).toList();
        if (!prices.isEmpty()) {
            Comparator<Money> byAmount = Comparator.comparingLong(Money::amount);
            Map<String, Object> offers = new LinkedHashMap<>();
            offers.put("@type", "AggregateOffer");
            offers.put("priceCurrency", prices.getFirst().currency());
            offers.put("lowPrice", decimal(prices.stream().min(byAmount).orElseThrow()));
            offers.put("highPrice", decimal(prices.stream().max(byAmount).orElseThrow()));
            offers.put("offerCount", prices.size());
            offers.put("availability", SCHEMA_AVAILABILITY.get(product.availability()));
            data.put("offers", offers);
        }
        return data;
    }

    /** "5.90": an amount as schema.org expects it (formatting only). */
    private static String decimal(Money money) {
        return BigDecimal.valueOf(money.amount(), 2).toPlainString();
    }

    private StringBuilder open(PageContext page) {
        StringBuilder body = new StringBuilder(4096).append("<div class=\"page-preview\">");
        writer.header(body, page);
        return body.append("<main id=\"main\">");
    }

    private static String close(StringBuilder body) {
        return body.append("</main></div>").toString();
    }

    private static List<CategoryNodeView> pathTo(List<CategoryNodeView> nodes, String slug) {
        for (CategoryNodeView node : nodes) {
            if (node.slug().equals(slug)) {
                return List.of(node);
            }
            List<CategoryNodeView> below = pathTo(node.children(), slug);
            if (!below.isEmpty()) {
                List<CategoryNodeView> path = new ArrayList<>(below.size() + 1);
                path.add(node);
                path.addAll(below);
                return path;
            }
        }
        return List.of();
    }

    private static String slug(String segment) {
        if (!SLUG.matcher(segment).matches()) {
            throw new NotFoundException("Not found.");
        }
        return segment;
    }

    /** "/shop/" → "/shop", "" → "/"; repeated slashes collapse. */
    static String normalise(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String collapsed = path.replaceAll("/{2,}", "/");
        if (!collapsed.startsWith("/")) {
            collapsed = "/" + collapsed;
        }
        return collapsed.length() > 1 && collapsed.endsWith("/")
                ? collapsed.substring(0, collapsed.length() - 1) : collapsed;
    }
}

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
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * answer 404, and so does every address the app has no page for ({@link AppRoutes}). The app's
 * other pages (account, cart, checkout, admin, …) get the template with only the language set.
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
    static final String GALLERY_SIZES = "(min-width: 1024px) 30vw, (min-width: 640px) 45vw, 92vw";
    static final int GALLERY_PRIORITY_PHOTOS = 2;

    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{0,199}");
    private static final int DESCRIPTION_LENGTH = 160;
    private static final String SCHEMA = "https://schema.org";
    /** The shop's logo mark in the frontend build (frontend/public/brand). */
    private static final String LOGO = "/brand/pw-aquatics-mark.svg";
    /** Shop parameters that make a filtered, searched or sorted view of the list (frontend shopFilters.ts). */
    private static final Set<String> SHOP_VIEW_PARAMETERS =
            Set.of("q", "species", "colour", "grade", "availability", "min", "max", "sort");
    private static final Pattern PAGE_NUMBER = Pattern.compile("[1-9][0-9]{0,5}");

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
     * @param path the requested page, e.g. "/de/products/blue-dream"
     * @param query the request's query string, or null; the shop reads its page number from it
     * @throws ShellSource.ShellUnavailableException if there is no template to fill in
     */
    public RenderedPage render(String path, @Nullable String query) {
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
            FilledPage filled = route(inLanguage, listPage(query), page, shell.routes());
            String head = filled.head() == null ? null : writer.head(filled.head(), page);
            String data = filled.data().isEmpty() ? null : json.writeValueAsString(filled.data());
            return new RenderedPage(filled.status(), shell.render(page.code(), head, filled.body(), data));
        } finally {
            LocaleContextHolder.setLocaleContext(previous);
        }
    }

    /** @param listPage the shop's page number (1-based), see {@link #listPage} */
    private FilledPage route(String path, int listPage, PageContext page, AppRoutes routes) {
        List<String> segments = "/".equals(path) ? List.of() : List.of(path.substring(1).split("/"));
        try {
            return switch (segments.size()) {
                case 0 -> home(page);
                case 1 -> switch (segments.getFirst()) {
                    case "shop" -> shop(page, null, listPage);
                    case "gallery" -> gallery(page);
                    case "about", "contact" -> intro(page, segments.getFirst());
                    default -> other(path, page, routes);
                };
                case 2 -> switch (segments.getFirst()) {
                    case "shop" -> shop(page, slug(segments.get(1)), listPage);
                    case "products" -> product(page, slug(segments.get(1)));
                    case "species" -> species(page, slug(segments.get(1)));
                    default -> other(path, page, routes);
                };
                default -> other(path, page, routes);
            };
        } catch (NotFoundException e) {
            return notFound(page);
        }
    }

    /** A page of the app the backend does not fill in, or "not found" where the app has no page. */
    private FilledPage other(String path, PageContext page, AppRoutes routes) {
        return routes.knows(path) ? FilledPage.plain() : notFound(page);
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
                PageHead.of(page.text("pages.home.title"), page.text("pages.home.description"), "/",
                        List.of(organisationData(page), webSiteData(page))),
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

    /** @param listPage the list's page (1-based); a page after the last answers 404 */
    private FilledPage shop(PageContext page, String categorySlug, int listPage) {
        List<CategoryNodeView> tree = catalog.categoryTree();
        List<CategoryNodeView> path = categorySlug == null ? List.of() : pathTo(tree, categorySlug);
        if (categorySlug != null && path.isEmpty()) {
            throw new NotFoundException("Category not found.");
        }
        CategoryNodeView category = path.isEmpty() ? null : path.getLast();
        ProductSearch search = new ProductSearch(
                null, categorySlug, null, null, null, null, null, null, false, ProductSort.RELEVANCE,
                listPage - 1, SHOP_PAGE_SIZE);
        Page<ProductSummaryView> products = catalog.searchProducts(search);
        if (listPage > 1 && products.isEmpty()) {
            throw new NotFoundException("Page not found.");
        }

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
        String list = categorySlug == null ? "/shop" : "/shop/" + categorySlug;
        writer.pagination(body, page, list, listPage, products.getTotalPages());

        // The app's queries for this list (frontend shopFilters.ts toProductQuery and
        // toFacetQuery) and the filter panel's lists, so the first render needs no request.
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("/categories", tree);
        data.put("/products?" + (categorySlug == null ? "" : "category=" + categorySlug + "&")
                        + (listPage > 1 ? "page=" + (listPage - 1) + "&" : "") + "size=" + SHOP_PAGE_SIZE,
                PageResponse.of(products));
        data.put("/products/facets" + (categorySlug == null ? "" : "?category=" + categorySlug), catalog.facets(search));
        data.put("/species", catalog.species());
        data.put("/colours", catalog.colours());
        data.put("/grades", catalog.grades());
        List<Map<String, Object>> structured = category == null
                ? List.of()
                : List.of(breadcrumbData(Stream.concat(
                        crumbs.stream(), Stream.of(new Crumb(category.name(), page.link(list)))).toList()));
        return new FilledPage(
                200,
                PageHead.of(title, description, listPage > 1 ? list + "?page=" + listPage : list, structured),
                close(body),
                data);
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
            // The app's sizes (frontend catalog ProductGallery).
            writer.image(body, photo, "(min-width: 1024px) 55vw, 100vw", true);
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
                List.of(productData(product, page), breadcrumbData(Stream.concat(
                        crumbs.stream(), Stream.of(new Crumb(product.name(), page.link(own)))).toList())));
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
                List.of());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("/species/" + slug, species);
        data.put("/products?species=" + slug + "&size=" + SPECIES_PAGE_SIZE, PageResponse.of(products));
        return new FilledPage(200, head, close(body), data);
    }

    private FilledPage gallery(PageContext page) {
        Page<GalleryImageView> photos = gallery.published(0, GALLERY_PAGE_SIZE);
        StringBuilder body = open(page);
        writer.heading(body, page.text("pages.gallery.heading"), page.text("pages.gallery.lead"));
        List<GalleryImageView> content = photos.getContent();
        for (int index = 0; index < content.size(); index++) {
            GalleryImageView photo = content.get(index);
            body.append("<figure>");
            // The app's sizes and first photos (frontend content GalleryGrid).
            writer.image(body, photo.image(), GALLERY_SIZES, index < GALLERY_PRIORITY_PHOTOS);
            String caption = Stream.of(photo.title(), photo.caption())
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining(" – "));
            if (!caption.isEmpty()) {
                body.append("<figcaption>").append(Html.text(caption)).append("</figcaption>");
            }
            body.append("</figure>");
        }
        // The app's first page of the gallery (frontend content/api/gallery.ts).
        return new FilledPage(
                200,
                PageHead.of(page.text("pages.gallery.title"), page.text("pages.gallery.description"), "/gallery"),
                close(body),
                Map.of("/gallery?page=0&size=" + GALLERY_PAGE_SIZE, PageResponse.of(photos)));
    }

    private FilledPage notFound(PageContext page) {
        StringBuilder body = open(page);
        writer.heading(body, page.text("notFound.heading"), page.text("notFound.text"));
        body.append("<p>");
        writer.link(body, page.link("/"), page.text("backToHome"));
        body.append("</p>");
        return new FilledPage(404, PageHead.notIndexed(page.text("notFound.title")), close(body), Map.of());
    }

    /**
     * schema.org Product with one offer per pack size. Only the backend writes structured data:
     * search engines read it from the first page load (the app keeps it until it leaves the page).
     */
    private Map<String, Object> productData(ProductDetailView product, PageContext page) {
        String url = links.page(page.code(), "/products/" + product.slug());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("@context", SCHEMA);
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
        if (!product.images().isEmpty()) {
            data.put("image", product.images().stream().map(image -> links.file(image.url())).toList());
        }
        data.put("url", url);
        if (!product.variants().isEmpty()) {
            data.put("offers", product.variants().stream().map(variant -> offer(product, variant, url)).toList());
        }
        return data;
    }

    private static Map<String, Object> offer(ProductDetailView product, VariantView variant, String url) {
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("@type", "Offer");
        offer.put("name", variant.name());
        offer.put("sku", variant.sku());
        offer.put("price", decimal(variant.price()));
        offer.put("priceCurrency", variant.price().currency());
        offer.put("availability", offerAvailability(product.availability(), variant.available()));
        offer.put("url", url);
        return offer;
    }

    /**
     * A pack size that can be bought now is in stock (limited when the product is low on stock);
     * one that cannot is out of stock, also while the product is "coming soon", since it cannot be
     * ordered in advance.
     */
    static String offerAvailability(Availability product, boolean variantAvailable) {
        if (!variantAvailable) {
            return SCHEMA + "/OutOfStock";
        }
        return product == Availability.LOW_STOCK ? SCHEMA + "/LimitedAvailability" : SCHEMA + "/InStock";
    }

    /** schema.org BreadcrumbList: the way from the shop to this page, the page itself last. */
    private Map<String, Object> breadcrumbData(List<Crumb> crumbs) {
        List<Map<String, Object>> items = new ArrayList<>(crumbs.size());
        for (int i = 0; i < crumbs.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("@type", "ListItem");
            item.put("position", i + 1);
            item.put("name", crumbs.get(i).label());
            item.put("item", links.file(crumbs.get(i).href()));
            items.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("@context", SCHEMA);
        data.put("@type", "BreadcrumbList");
        data.put("itemListElement", items);
        return data;
    }

    /** schema.org Organization (the shop, with its logo) for the home page. */
    private Map<String, Object> organisationData(PageContext page) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("@context", SCHEMA);
        data.put("@type", "Organization");
        data.put("name", page.text("brand"));
        data.put("url", links.page("en", "/"));
        data.put("logo", links.file(LOGO));
        return data;
    }

    /** schema.org WebSite (the shop's name for search results) for the home page. */
    private Map<String, Object> webSiteData(PageContext page) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("@context", SCHEMA);
        data.put("@type", "WebSite");
        data.put("name", page.text("brand"));
        data.put("url", links.page(page.code(), "/"));
        data.put("inLanguage", page.code());
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

    /**
     * The page of the plain product list a shop link asks for ({@code ?page=2}); 1 for a filtered,
     * searched or sorted view, which the app's canonical link points to the list's first page for
     * (frontend shopFilters.ts {@code canonicalPage}).
     */
    static int listPage(@Nullable String query) {
        if (query == null || query.isBlank()) {
            return 1;
        }
        int page = 1;
        for (String parameter : query.split("&")) {
            int equals = parameter.indexOf('=');
            String name = URLDecoder.decode(equals < 0 ? parameter : parameter.substring(0, equals), StandardCharsets.UTF_8);
            String value = equals < 0 ? "" : URLDecoder.decode(parameter.substring(equals + 1), StandardCharsets.UTF_8);
            if (SHOP_VIEW_PARAMETERS.contains(name) && !value.isBlank()) {
                return 1;
            }
            if (name.equals("page") && PAGE_NUMBER.matcher(value).matches()) {
                page = Integer.parseInt(value);
            }
        }
        return page;
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

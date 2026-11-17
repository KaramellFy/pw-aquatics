package com.pwaquatics.shop.pages.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pwaquatics.shop.common.text.ContentLanguage;
import com.pwaquatics.shop.support.FakeShellSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PageShellTest {

    private final PageTexts texts = PageTexts.of(new ObjectMapper().readTree(FakeShellSource.TEXTS));
    private final AppRoutes routes = AppRoutes.of(new ObjectMapper().readTree(FakeShellSource.ROUTES));

    @Test
    void fillsInTheLanguageHeadTextAndData() {
        PageShell shell = PageShell.parse(FakeShellSource.TEMPLATE, texts, routes);
        String html = shell.render("de", "<title data-page>Seite</title>", "<h1>Hallo</h1>", "{\"/x\":\"</script>\"}");

        assertThat(html)
                .contains("<html lang=\"de\">", "<title data-page>Seite</title></head>")
                .doesNotContain("<title>PW Aquatics</title>", "Default description.")
                .contains("<div id=\"root\"><h1>Hallo</h1></div>")
                // Data can never close its script element.
                .contains("<script type=\"application/json\" id=\"page-data\">{\"/x\":\"\\u003c/script>\"}</script></body>")
                .contains("<script type=\"module\" crossorigin src=\"/assets/index-abc.js\"></script>");
    }

    @Test
    void pagesThatAreNotFilledInKeepTheDefaultTitle() {
        String html = PageShell.parse(FakeShellSource.TEMPLATE, texts, routes).render("en", null, null, null);
        assertThat(html)
                .contains("<html lang=\"en\">", "<title>PW Aquatics</title>", "Default description.", "<div id=\"root\"></div>")
                .doesNotContain("page-data");
    }

    @Test
    void refusesATemplateWithoutTheMarkedPlaces() {
        assertThatThrownBy(() -> PageShell.parse("<html lang=\"en\"><head></head><body><div id=\"app\"></div></body></html>", texts, routes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<div id=\"root\"></div>");
    }

    @Test
    void textsFallBackToEnglishAndFillPlaceholders() {
        assertThat(texts.get(ContentLanguage.DE, "nav.gallery")).isEqualTo("Galerie");
        assertThat(texts.get(ContentLanguage.DE, "pages.contact.title")).isEqualTo("Contact – PW Aquatics");
        assertThat(texts.get(ContentLanguage.DE, "no.such.key")).isEmpty();
        assertThat(texts.format(ContentLanguage.DE, "product.from", "price", "3,00 €")).isEqualTo("ab 3,00 €");
    }

    @Test
    void knowsTheAppsAddressesLikeItsRouter() {
        assertThat(routes.knows("/")).isTrue();
        assertThat(routes.knows("/account/orders/PW-2026-000001")).isTrue();
        assertThat(routes.knows("/Account/Orders")).isTrue();
        assertThat(routes.knows("/admin/anything/below")).isTrue();
        assertThat(routes.knows("/admin")).isTrue();
        assertThat(routes.knows("/products")).isFalse();
        assertThat(routes.knows("/products/a/b")).isFalse();
        assertThat(routes.knows("/foo")).isFalse();
        assertThat(routes.knows("/wp-login")).isFalse();
        // A build without the list must not turn the app's pages into 404s.
        assertThat(AppRoutes.of(null).knows("/foo")).isTrue();
    }

    @Test
    void normalisesPaths() {
        assertThat(PageService.normalise("")).isEqualTo("/");
        assertThat(PageService.normalise("/shop/")).isEqualTo("/shop");
        assertThat(PageService.normalise("//de//shop")).isEqualTo("/de/shop");
        assertThat(PageService.normalise("/")).isEqualTo("/");
    }
}

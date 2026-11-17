package com.pwaquatics.shop.pages.application;

import com.pwaquatics.shop.common.money.Money;
import com.pwaquatics.shop.common.text.ContentLanguage;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** The language and interface texts of the page being filled in. */
record PageContext(ContentLanguage language, PageTexts texts) {

    /** As in emails and the shop: "5,90 €" in German, "€5.90" in English. */
    private static final Locale ENGLISH_MONEY = Locale.of("en", "IE");

    String code() {
        return language.name().toLowerCase(Locale.ROOT);
    }

    String text(String key) {
        return texts.get(language, key);
    }

    /** A link within the shop in this page's language ("/shop" → "/de/shop" in German). */
    String link(String path) {
        if (language != ContentLanguage.DE) {
            return path;
        }
        return "/".equals(path) ? "/de/" : "/de" + path;
    }

    /** Formats an amount from the API for reading; no arithmetic. */
    String money(Money money) {
        NumberFormat format = NumberFormat.getCurrencyInstance(language == ContentLanguage.DE ? Locale.GERMANY : ENGLISH_MONEY);
        format.setCurrency(Currency.getInstance(money.currency()));
        return format.format(BigDecimal.valueOf(money.amount(), 2));
    }

    Locale locale() {
        return language == ContentLanguage.DE ? Locale.GERMAN : Locale.ENGLISH;
    }
}

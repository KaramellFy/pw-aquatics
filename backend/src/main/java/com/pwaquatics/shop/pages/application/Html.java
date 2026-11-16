package com.pwaquatics.shop.pages.application;

import java.util.regex.Pattern;

/** Escaping for text the backend writes into page HTML. */
final class Html {

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SPACE = Pattern.compile("\\s+");

    private Html() {}

    /** Text between tags. */
    static String text(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /** A double-quoted attribute value. */
    static String attribute(String value) {
        return text(value);
    }

    /**
     * JSON inside a {@code <script>} data block: "<" is written as "\u003c", so the text can
     * never close the script element or open a comment.
     */
    static String jsonInScript(String json) {
        return json.replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }

    /** Plain text of sanitised HTML (for a meta description), with whitespace collapsed. */
    static String plainText(String html) {
        if (html == null) {
            return null;
        }
        String text = TAG.matcher(html).replaceAll(" ");
        text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ").replace("&amp;", "&");
        return SPACE.matcher(text).replaceAll(" ").strip();
    }
}

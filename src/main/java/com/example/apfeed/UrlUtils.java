package com.example.apfeed;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

public class UrlUtils {
    // paths we want to ignore completely
    private static final String[] EXCLUDE_PATTERNS = {
            "/cart", "/basket", "/checkout", "/account", "/login", "/register",
            "/wishlist", "/search", "/filter", "/tag/", "/tags/",
            "/privacy", "/terms", "/contact", "/about", "/faq", "/faqs", "/help",
            "\\.(pdf|jpg|jpeg|png|gif|webp|svg|ico|css|js|mp4|webm)$"
    };

    private static final Pattern excludeRe = Pattern.compile(
            String.join("|", EXCLUDE_PATTERNS),
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Very simple "does this look like a product page URL?"
     * - Accepts /product/, /products/, /item/, /sku/, /shop/, /p/, /pd/, /prod/
     * - Rejects anything matching EXCLUDE_PATTERNS
     */
    public static boolean looksLikeProductPath(String path) {
        if (path == null) return false;
        if (excludeRe.matcher(path).find()) return false;

        String p = path.toLowerCase();
        return p.contains("/product/")
                || p.contains("/products/")
                || p.contains("/item/")
                || p.contains("/sku/")
                || p.contains("/shop/")
                || p.contains("/p/")
                || p.contains("/pd/")
                || p.contains("/prod/");
    }

    public static boolean isSameSite(String a, String b) {
        try {
            URI ua = new URI(a);
            URI ub = new URI(b);
            String ha = ua.getHost() != null ? ua.getHost().toLowerCase() : "";
            String hb = ub.getHost() != null ? ub.getHost().toLowerCase() : "";
            return !ha.isEmpty() && ha.equals(hb);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static String normalizeUrl(String baseUrl, String href) {
        try {
            URI base = new URI(baseUrl);
            URI resolved = base.resolve(href);
            String path = resolved.getPath();
            if (path == null) path = "";
            path = path.replaceAll("/{2,}", "/");
            URI cleaned = new URI(
                    resolved.getScheme(),
                    resolved.getUserInfo(),
                    resolved.getHost() != null ? resolved.getHost().toLowerCase() : null,
                    resolved.getPort(),
                    path,
                    resolved.getQuery(),
                    null // strip fragment
            );
            return cleaned.toString();
        } catch (URISyntaxException e) {
            return href;
        }
    }
}

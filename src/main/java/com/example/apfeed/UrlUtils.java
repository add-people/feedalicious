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
     * Very generic "does this look like a product page URL?"
     *
     * - Rejects anything matching EXCLUDE_PATTERNS
     * - Accepts classic ecom patterns: /product/, /products/, /item/, /sku/, /shop/, /store/, /p/, /pd/, /prod/
     * - OR: last path segment looks like a product slug, eg "3-shears-sale" or "hair-scissors-123"
     */
    public static boolean looksLikeProductPath(String path) {
        if (path == null) return false;
        if (excludeRe.matcher(path).find()) return false;

        String p = path.toLowerCase();

        // classic ecom hints
        if (p.contains("/product/")
                || p.contains("/products/")
                || p.contains("/item/")
                || p.contains("/sku/")
                || p.contains("/shop/")
                || p.contains("/store/")
                || p.contains("/p/")
                || p.contains("/pd/")
                || p.contains("/prod/")) {
            return true;
        }

        // check the last segment for "product-like" slug
        String last = p;
        int idx = p.lastIndexOf('/');
        if (idx >= 0) {
            last = p.substring(idx + 1);
        }
        if (last.isBlank()) return false;

        // Strip common page extensions
        last = last.replaceAll("\\.(html|htm|php|asp|aspx)$", "");

        // Short, generic segments are unlikely to be products
        if (last.length() < 4) return false;

        // Heuristic: product slugs usually have '-' or digits
        boolean hasDash = last.contains("-");
        boolean hasDigit = last.matches(".*[0-9].*");

        if (hasDash && last.length() >= 5) return true;
        if (hasDigit && last.length() >= 5) return true;

        return false;
    }

    /**
     * Loose "listing/category" detector:
     * True for /category/, /categories/, /collections/, /shop/, /store/, /products (without a slug), etc.
     */
    public static boolean looksLikeListingPath(String path) {
        if (path == null) return false;
        if (excludeRe.matcher(path).find()) return false;

        String p = path.toLowerCase();

        // Obvious category/listing patterns
        if (p.contains("/category") || p.contains("/categories")
                || p.contains("/collection") || p.contains("/collections")
                || p.contains("/catalog") || p.contains("/shop")
                || p.contains("/store")) {
            return true;
        }

        // "/products" or "/products/" often means a generic listing
        if (p.equals("/products") || p.equals("/products/")) {
            return true;
        }

        return false;
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

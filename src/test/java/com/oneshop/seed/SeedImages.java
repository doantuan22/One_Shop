package com.oneshop.seed;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Image pipeline of the large seed (single file, JDK only):
 * <pre>
 *   java src/test/java/com/oneshop/seed/SeedImages.java --fetch-logos     [--work D:/tmp/oneshop-seed-images]
 *   java src/test/java/com/oneshop/seed/SeedImages.java --process         [--work ...]
 *   java src/test/java/com/oneshop/seed/SeedImages.java --upload [--dry-run]   (needs CLOUDINARY_SEED_UPLOAD=true)
 *   java src/test/java/com/oneshop/seed/SeedImages.java --missing
 *   java src/test/java/com/oneshop/seed/SeedImages.java --cleanup [--dry-run]  (deletes the prefix oneshop/seed/ only)
 *   java src/test/java/com/oneshop/seed/SeedImages.java --delete-ids backup/old_cloudinary_public_ids.txt
 * </pre>
 * Never fabricates URLs: a key that is not downloaded/uploaded stays missing and is reported. Secrets are never printed.
 * Originals live in the work directory (outside the repo); only image_sources.csv and image_manifest.csv are committed.
 */
public final class SeedImages {

    static final Path IMAGES_DIR = Path.of("database/seed_large/images");
    static final Path PLAN = IMAGES_DIR.resolve("image_plan.csv");
    static final Path SOURCES = IMAGES_DIR.resolve("image_sources.csv");
    static final Path MANIFEST = IMAGES_DIR.resolve("image_manifest.csv");
    static final String SOURCES_HEADER = "key,type,page_url,download_url,author,license,license_url,downloaded_on,use";
    static final String UA = "OneShopSeedTool/1.0 (internal development seed; https://github.com/doantuan22)";
    static final int MAX_PRODUCT_BYTES = 300 * 1024, MAX_LOGO_BYTES = 150 * 1024, MAX_EDGE = 1200, MAX_LOGO_WIDTH = 600;
    static final String PUBLIC_ID_ROOT = "oneshop/seed";

    /** brand slug -> Wikimedia Commons file (official logo uploads; license read from the file page via the API). */
    static final Map<String, String> COMMONS_LOGOS = new LinkedHashMap<>();
    static {
        COMMONS_LOGOS.put("loreal-paris", "File:L'Oréal logo.svg");
        COMMONS_LOGOS.put("maybelline-new-york", "File:Maybelline-Logo.svg");
        COMMONS_LOGOS.put("garnier", "File:Garnier wordmark.svg");
        COMMONS_LOGOS.put("neutrogena", "File:Neutrogena logo.svg");
        COMMONS_LOGOS.put("la-roche-posay", "File:La Roche-Posay (brand).svg");
        COMMONS_LOGOS.put("cetaphil", "File:Cetaphil Thailand Logo.png");
        COMMONS_LOGOS.put("cerave", "File:CeraVe logo.png");
        COMMONS_LOGOS.put("nivea", "File:NIVEA logo 2021.svg");
        COMMONS_LOGOS.put("dove", "File:Dove-logo-2012.png");
        COMMONS_LOGOS.put("pantene", "File:Pantene.svg");
        COMMONS_LOGOS.put("bath-and-body-works", "File:Bath & Body Works logo.svg");
        COMMONS_LOGOS.put("victorias-secret", "File:Victoria's Secret logo.svg");
        COMMONS_LOGOS.put("innisfree", "File:Innisfree logo.png");
    }
    static final Map<String, String> BRAND_NAMES = new LinkedHashMap<>();

    record PlanRow(String key, String source, String publicId) { }

    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    // ------------------------------------------------------------ signature rules (same as CloudinaryServiceImpl)

    /** Same acceptance rule as CloudinaryServiceImpl.validateFileSignature: JPEG, PNG or WebP only (SVG/text/exe rejected). */
    public static boolean hasAllowedSignature(byte[] b) {
        if (b == null || b.length < 12) return false;
        boolean jpeg = (b[0] == (byte) 0xFF) && (b[1] == (byte) 0xD8) && (b[2] == (byte) 0xFF);
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        boolean isPng = true;
        for (int i = 0; i < png.length; i++) if (b[i] != png[i]) { isPng = false; break; }
        boolean webp = b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        return jpeg || isPng || webp;
    }

    // ------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        Set<String> a = new HashSet<>(Arrays.asList(args));
        Path work = Path.of(System.getProperty("java.io.tmpdir"), "oneshop-seed-images");
        Path deleteIds = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--work")) work = Path.of(args[++i]);
            if (args[i].equals("--delete-ids")) deleteIds = Path.of(args[++i]);
        }
        boolean dry = a.contains("--dry-run");
        Files.createDirectories(work.resolve("logos"));
        Files.createDirectories(work.resolve("stock"));
        Files.createDirectories(work.resolve("processed"));
        if (a.contains("--fetch-logos")) fetchLogos(work);
        if (a.contains("--process")) process(work);
        if (a.contains("--missing")) missing(work);
        if (a.contains("--upload")) upload(work, dry);
        if (a.contains("--cleanup")) cleanup(dry);
        if (deleteIds != null) deleteIds(deleteIds, dry);
        if (args.length == 0) System.out.println("Usage: --fetch-logos | --process | --missing | --upload [--dry-run] | --cleanup [--dry-run] | --delete-ids <file> [--work <dir>]");
    }

    // ------------------------------------------------------------ plan / ledger helpers

    static List<PlanRow> plan() throws IOException {
        List<PlanRow> rows = new ArrayList<>();
        List<String> lines = Files.readAllLines(PLAN, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split(",", -1);
            if (c.length >= 3) rows.add(new PlanRow(c[0], c[1], c[2]));
        }
        return rows;
    }

    static Map<String, String[]> sources() throws IOException {
        Map<String, String[]> map = new LinkedHashMap<>();
        if (!Files.exists(SOURCES)) return map;
        for (String line : Files.readAllLines(SOURCES, StandardCharsets.UTF_8).stream().skip(1).toList()) {
            List<String> c = csv(line);
            if (c.size() >= 9) map.put(c.get(0), c.toArray(new String[0]));
        }
        return map;
    }

    static List<String> csv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (q) { if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; } else if (ch == '"') q = false; else cur.append(ch); }
            else if (ch == '"') q = true; else if (ch == ',') { out.add(cur.toString()); cur.setLength(0); } else cur.append(ch);
        }
        out.add(cur.toString());
        return out;
    }

    static String esc(String s) { return s == null ? "" : (s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s); }

    static void writeSources(Map<String, String[]> map) throws IOException {
        StringBuilder sb = new StringBuilder(SOURCES_HEADER).append('\n');
        for (String[] r : map.values()) {
            for (int i = 0; i < r.length; i++) { if (i > 0) sb.append(','); sb.append(esc(r[i])); }
            sb.append('\n');
        }
        Files.writeString(SOURCES, sb.toString(), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------ fetch logos (Wikimedia Commons)

    static void fetchLogos(Path work) throws Exception {
        Map<String, String[]> src = sources();
        for (PlanRow row : plan()) {
            if (!row.source().startsWith("logo:")) continue;
            String slug = row.source().substring(5);
            Path target = work.resolve("logos").resolve(slug + ".png");
            String title = COMMONS_LOGOS.get(slug);
            if (title == null) {
                // no trustworthy source found: simple generated text logo (documented in image_sources.csv)
                if (!Files.exists(target)) Files.write(target, textLogo(prettyName(slug)));
                src.put(row.key(), new String[]{row.key(), "generated-text-logo", "", "", "OneShop seed tool", "n/a (generated)", "", LocalDate.now().toString(), "internal-test-only"});
                System.out.println("generated text logo: " + slug);
                continue;
            }
            String api = "https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url%7Cextmetadata%7Cmime&iiurlwidth="
                    + MAX_LOGO_WIDTH + "&titles=" + URLEncoder.encode(title, StandardCharsets.UTF_8);
            String json = get(api);
            String thumb = jsonString(json, "thumburl"), page = jsonString(json, "descriptionurl");
            if (thumb == null) { System.err.println("NOT FOUND on Commons: " + title); continue; }
            String author = stripTags(extmeta(json, "Artist")), license = extmeta(json, "LicenseShortName"), licenseUrl = extmeta(json, "LicenseUrl");
            byte[] bytes = getBytes(thumb);
            if (!hasAllowedSignature(bytes)) { System.err.println("not PNG/JPEG/WebP after rasterising: " + title); continue; }
            Files.write(target, bytes);
            src.put(row.key(), new String[]{row.key(), "brand-logo", page, thumb, author, license, licenseUrl, LocalDate.now().toString(), "internal-test-only"});
            System.out.println("logo " + slug + " <- " + title + " (" + license + ", " + bytes.length + " bytes)");
            Thread.sleep(1200);
        }
        writeSources(src);
    }

    static String prettyName(String slug) {
        Map<String, String> names = Map.ofEntries(Map.entry("senka", "SENKA"), Map.entry("dear-klairs", "Dear Klairs"), Map.entry("some-by-mi", "SOME BY MI"),
                Map.entry("skin1004", "SKIN1004"), Map.entry("anessa", "ANESSA"), Map.entry("romand", "rom&nd"), Map.entry("3ce", "3CE"),
                Map.entry("bbia", "BBIA"), Map.entry("cocoon", "COCOON"));
        return names.getOrDefault(slug, slug.replace('-', ' ').toUpperCase(Locale.ROOT));
    }

    static byte[] textLogo(String text) throws IOException {
        BufferedImage img = new BufferedImage(480, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0xF3EFEA)); g.fillRect(0, 0, 480, 240);
        g.setColor(new Color(0x4A4540));
        int size = 64;
        Font font;
        FontMetrics fm;
        do { font = new Font("SansSerif", Font.BOLD, size); g.setFont(font); fm = g.getFontMetrics(); size -= 4; } while (fm.stringWidth(text) > 420 && size > 16);
        g.drawString(text, (480 - fm.stringWidth(text)) / 2, 120 + fm.getAscent() / 3);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    // ------------------------------------------------------------ process

    static void process(Path work) throws Exception {
        Map<String, String[]> src = sources();
        int ok = 0, skipped = 0;
        for (PlanRow row : plan()) {
            Path in = originalFor(work, row);
            if (in == null || !Files.exists(in)) { skipped++; continue; }
            // stock photos need a documented license row; static repo files and generated logos are exempt
            if (row.source().startsWith("stock:") && !licensed(src, row)) { System.err.println("no license row for " + row.source() + " in image_sources.csv: skipped"); skipped++; continue; }
            boolean logo = row.key().startsWith("brand:");
            byte[] raw = Files.readAllBytes(in);
            byte[] out;
            String ext;
            if (row.source().startsWith("static:")) {
                if (!hasAllowedSignature(raw) || raw.length > MAX_PRODUCT_BYTES) throw new IOException("static image not acceptable: " + in);
                int[] wh = webpSize(raw);
                if (wh != null && Math.max(wh[0], wh[1]) > MAX_EDGE) throw new IOException("static image longer than " + MAX_EDGE + "px: " + in);
                out = raw; ext = "webp";
            } else {
                BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(raw));
                if (img == null) { System.err.println("unreadable image: " + in); skipped++; continue; }
                int limitEdge = logo ? MAX_LOGO_WIDTH : MAX_EDGE;
                int limitBytes = logo ? MAX_LOGO_BYTES : MAX_PRODUCT_BYTES;
                BufferedImage scaled = scale(img, limitEdge, logo);
                boolean alpha = scaled.getColorModel().hasAlpha();
                if (logo || alpha) {
                    out = encodePng(scaled);
                    ext = "png";
                    while (out.length > limitBytes && Math.max(scaled.getWidth(), scaled.getHeight()) > 120) {
                        scaled = scale(scaled, (int) (Math.max(scaled.getWidth(), scaled.getHeight()) * 0.8), logo);
                        out = encodePng(scaled);
                    }
                } else {
                    float q = 0.9f;
                    out = encodeJpeg(scaled, q);
                    while (out.length > limitBytes && q > 0.4f) { q -= 0.1f; out = encodeJpeg(scaled, q); }
                    ext = "jpg";
                }
                if (out.length > limitBytes) throw new IOException("cannot reduce below limit: " + row.key());
            }
            if (!hasAllowedSignature(out)) throw new IOException("signature check failed: " + row.key());
            Files.write(work.resolve("processed").resolve(safe(row.key()) + "." + ext), out);
            ok++;
        }
        System.out.println("processed=" + ok + " skipped(no source yet)=" + skipped);
    }

    static boolean licensed(Map<String, String[]> src, PlanRow row) {
        // stock files are keyed by their source key (e.g. stock:serum-1) in image_sources.csv
        String[] r = src.get(row.source());
        return r != null && r.length >= 9 && !r[2].isBlank() && !r[5].isBlank() && !r[6].isBlank();
    }

    static Path originalFor(Path work, PlanRow row) throws IOException {
        String s = row.source();
        if (s.startsWith("logo:")) return work.resolve("logos").resolve(s.substring(5) + ".png");
        if (s.startsWith("static:")) return Path.of("src/main/resources/static/images/site").resolve(s.substring(7));
        if (s.startsWith("stock:")) {
            for (String ext : new String[]{"jpg", "jpeg", "png", "webp"}) {
                Path p = work.resolve("stock").resolve(s.substring(6) + "." + ext);
                if (Files.exists(p)) return p;
            }
            return work.resolve("stock").resolve(s.substring(6) + ".jpg");
        }
        return null;
    }

    static String safe(String key) { return key.replaceAll("[^A-Za-z0-9._-]+", "_"); }

    static BufferedImage scale(BufferedImage in, int maxEdge, boolean logo) {
        int w = in.getWidth(), h = in.getHeight();
        double f = Math.min(1.0, (double) maxEdge / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * f)), nh = Math.max(1, (int) Math.round(h * f));
        boolean alpha = in.getColorModel().hasAlpha();
        BufferedImage out = new BufferedImage(nw, nh, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (!alpha) { g.setColor(Color.WHITE); g.fillRect(0, 0, nw, nh); }
        g.drawImage(in, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    static byte[] encodePng(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    static byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        BufferedImage rgb = img;
        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight()); g.drawImage(img, 0, 0, null); g.dispose();
        }
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(quality);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            w.setOutput(ios);
            w.write(null, new IIOImage(rgb, null, null), p);
        }
        w.dispose();
        return out.toByteArray();
    }

    /** Canvas size of a VP8X WebP, or null for other WebP flavours. */
    static int[] webpSize(byte[] b) {
        if (b.length < 30 || b[12] != 'V' || b[13] != 'P' || b[14] != '8' || b[15] != 'X') return null;
        int w = 1 + ((b[24] & 0xFF) | ((b[25] & 0xFF) << 8) | ((b[26] & 0xFF) << 16));
        int h = 1 + ((b[27] & 0xFF) | ((b[28] & 0xFF) << 8) | ((b[29] & 0xFF) << 16));
        return new int[]{w, h};
    }

    // ------------------------------------------------------------ missing

    static void missing(Path work) throws IOException {
        Map<String, String[]> src = sources();
        Set<String> need = new TreeSet<>();
        for (PlanRow row : plan()) {
            if (!row.source().startsWith("stock:")) continue;
            Path p = originalFor(work, row);
            if (p == null || !Files.exists(p) || !licensed(src, row)) need.add(row.source());
        }
        System.out.println("Stock photos still missing (" + need.size() + "). Put <name>.jpg in " + work.resolve("stock") + " and add a license row per key to " + SOURCES + ":");
        need.forEach(k -> System.out.println("  " + k));
        System.out.println("Then run: --process, then CLOUDINARY_SEED_UPLOAD=true ... --upload");
    }

    // ------------------------------------------------------------ upload / cleanup

    record Cloud(String name, String key, String secret) { }

    static Cloud cloud() throws IOException {
        Map<String, String> env = new HashMap<>(System.getenv());
        Path dotenv = Path.of(".env");
        if (Files.exists(dotenv)) {
            for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                Matcher m = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$").matcher(line);
                if (m.matches()) env.putIfAbsent(m.group(1), m.group(2).replaceAll("^\"|\"$", ""));
            }
        }
        String n = env.get("CLOUDINARY_CLOUD_NAME"), k = env.get("CLOUDINARY_API_KEY"), s = env.get("CLOUDINARY_API_SECRET");
        if (n == null || n.isBlank() || k == null || k.isBlank() || s == null || s.isBlank())
            throw new IllegalStateException("CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET are required.");
        return new Cloud(n, k, s);
    }

    static void requireSeedUploadFlag() {
        if (!"true".equalsIgnoreCase(System.getenv("CLOUDINARY_SEED_UPLOAD")))
            throw new IllegalStateException("Refusing to touch Cloudinary: set CLOUDINARY_SEED_UPLOAD=true (or use --dry-run).");
    }

    static void upload(Path work, boolean dry) throws Exception {
        Map<String, String[]> manifest = new HashMap<>();
        if (Files.exists(MANIFEST)) {
            List<String> ml = Files.readAllLines(MANIFEST, StandardCharsets.UTF_8);
            for (int i = 1; i < ml.size(); i++) { String[] p = ml.get(i).split(",", -1); if (p.length >= 3) manifest.put(p[0], p); }
        }
        List<PlanRow> todo = new ArrayList<>();
        List<String> noFile = new ArrayList<>();
        Map<String, Path> files = new LinkedHashMap<>();
        for (PlanRow row : plan()) {
            Path p = null;
            try (var s = Files.list(work.resolve("processed"))) {
                p = s.filter(f -> f.getFileName().toString().startsWith(safe(row.key()) + ".")).findFirst().orElse(null);
            }
            if (p == null) { noFile.add(row.key()); continue; }
            files.put(row.key(), p);
            if (!manifest.containsKey(row.key())) todo.add(row);
        }
        System.out.println("plan keys=" + plan().size() + " processed=" + files.size() + " already uploaded=" + manifest.size() + " to upload=" + todo.size() + " without processed file=" + noFile.size());
        if (dry) {
            todo.forEach(r -> System.out.println("  would upload " + r.key() + " -> " + r.publicId() + " (" + files.get(r.key()).getFileName() + ")"));
            if (!noFile.isEmpty()) System.out.println("  still missing: " + noFile.size() + " keys (run --missing)");
            return;
        }
        requireSeedUploadFlag();
        Cloud c = cloud();
        Map<String, String[]> rows = new LinkedHashMap<>();
        List<String> existing = Files.exists(MANIFEST) ? Files.readAllLines(MANIFEST, StandardCharsets.UTF_8) : List.of();
        for (int i = 1; i < existing.size(); i++) { String[] p = existing.get(i).split(",", -1); if (p.length >= 6) rows.put(p[0], p); }
        for (PlanRow r : todo) {
            byte[] data = Files.readAllBytes(files.get(r.key()));
            if (!hasAllowedSignature(data)) throw new IOException("signature check failed: " + r.key());
            String json = uploadOne(c, r.publicId(), files.get(r.key()).getFileName().toString(), data);
            String url = jsonString(json, "secure_url"), pid = jsonString(json, "public_id");
            if (url == null || pid == null || !url.startsWith("https://res.cloudinary.com/")) throw new IOException("unexpected Cloudinary response for " + r.key());
            rows.put(r.key(), new String[]{r.key(), pid, url, String.valueOf(jsonNumber(json, "width")), String.valueOf(jsonNumber(json, "height")), String.valueOf(jsonNumber(json, "bytes"))});
            System.out.println("uploaded " + r.key() + " -> " + pid);
        }
        StringBuilder sb = new StringBuilder("key,public_id,secure_url,width,height,bytes\n");
        rows.values().stream().sorted(Comparator.comparing(x -> x[0])).forEach(x -> sb.append(String.join(",", x)).append('\n'));
        Files.writeString(MANIFEST, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("manifest entries: " + rows.size() + " / " + plan().size());
        if (!noFile.isEmpty()) System.out.println("Missing (not uploaded): " + noFile.size() + " keys - run --missing for the list.");
    }

    static String uploadOne(Cloud c, String publicId, String filename, byte[] data) throws Exception {
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        String toSign = "invalidate=true&overwrite=true&public_id=" + publicId + "&timestamp=" + ts + c.secret();
        String signature = sha1(toSign);
        String boundary = "----seed" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("api_key", c.key()); fields.put("timestamp", ts); fields.put("signature", signature);
        fields.put("public_id", publicId); fields.put("overwrite", "true"); fields.put("invalidate", "true");
        for (var e : fields.entrySet()) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n" + e.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(data);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.cloudinary.com/v1_1/" + c.name() + "/image/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) throw new IOException("Cloudinary upload failed (HTTP " + resp.statusCode() + ") for " + publicId + ": " + abbreviate(resp.body()));
        return resp.body();
    }

    static void cleanup(boolean dry) throws Exception {
        String prefix = PUBLIC_ID_ROOT + "/";
        if (dry) { System.out.println("would delete every image with prefix " + prefix + " (and clear image_manifest.csv)"); return; }
        requireSeedUploadFlag();
        Cloud c = cloud();
        URI uri = URI.create("https://api.cloudinary.com/v1_1/" + c.name() + "/resources/image/upload?prefix=" + URLEncoder.encode(prefix, StandardCharsets.UTF_8) + "&invalidate=true");
        HttpRequest req = HttpRequest.newBuilder(uri).header("Authorization", basic(c)).DELETE().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) throw new IOException("cleanup failed (HTTP " + resp.statusCode() + "): " + abbreviate(resp.body()));
        System.out.println("cleanup done for prefix " + prefix + ": " + abbreviate(resp.body()));
        Files.deleteIfExists(MANIFEST);
    }

    static void deleteIds(Path file, boolean dry) throws Exception {
        List<String> ids = Files.readAllLines(file, StandardCharsets.UTF_8).stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        System.out.println("public ids listed: " + ids.size());
        if (dry || ids.isEmpty()) { System.out.println(dry ? "dry-run: nothing deleted" : "nothing to delete"); return; }
        requireSeedUploadFlag();
        Cloud c = cloud();
        for (int i = 0; i < ids.size(); i += 100) {
            StringBuilder q = new StringBuilder("invalidate=true");
            for (String id : ids.subList(i, Math.min(ids.size(), i + 100))) q.append("&public_ids[]=").append(URLEncoder.encode(id, StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.cloudinary.com/v1_1/" + c.name() + "/resources/image/upload?" + q))
                    .header("Authorization", basic(c)).DELETE().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) throw new IOException("delete failed (HTTP " + resp.statusCode() + "): " + abbreviate(resp.body()));
        }
        System.out.println("deleted listed assets: " + ids.size());
    }

    // ------------------------------------------------------------ tiny helpers

    static String basic(Cloud c) { return "Basic " + Base64.getEncoder().encodeToString((c.key() + ":" + c.secret()).getBytes(StandardCharsets.UTF_8)); }

    static String sha1(String s) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).GET().build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " for " + url);
        return r.body();
    }

    static byte[] getBytes(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).GET().build();
        HttpResponse<byte[]> r = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " for " + url);
        return r.body();
    }

    static String jsonString(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        return m.find() ? unescape(m.group(1)) : null;
    }

    static long jsonNumber(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    /** Value of an extmetadata entry: "Key":{"value":"...", ...}. */
    static String extmeta(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\{\\s*\"value\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        return m.find() ? unescape(m.group(1)) : "";
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                if (n == 'u' && i + 4 < s.length()) { sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; }
                else if (n == 'n') sb.append(' ');
                else sb.append(n);
            } else sb.append(c);
        }
        return sb.toString();
    }

    static String stripTags(String s) {
        String t = s == null ? "" : s.replaceAll("<[^>]*>", "").replace("\\n", " ").trim();
        int h = t.length() / 2;
        return t.length() > 1 && t.length() % 2 == 0 && t.substring(0, h).equals(t.substring(h)) ? t.substring(0, h) : t; // "AA" from nested tags
    }

    static String abbreviate(String s) { return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "..." : s).replaceAll("api_secret[^,}]*", "api_secret=***"); }
}

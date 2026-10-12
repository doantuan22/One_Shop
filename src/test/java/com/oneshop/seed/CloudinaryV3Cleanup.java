package com.oneshop.seed;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CloudinaryV3Cleanup {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    record Cloud(String name, String key, String secret) {}

    static Cloud loadCloud() throws IOException {
        Map<String, String> env = new HashMap<>(System.getenv());
        Path dotenv = Path.of(".env");
        if (Files.exists(dotenv)) {
            for (String line : Files.readAllLines(dotenv, StandardCharsets.UTF_8)) {
                Matcher m = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$").matcher(line);
                if (m.matches()) env.putIfAbsent(m.group(1), m.group(2).replaceAll("^\"|\"$", ""));
            }
        }
        String n = env.get("CLOUDINARY_CLOUD_NAME");
        String k = env.get("CLOUDINARY_API_KEY");
        String s = env.get("CLOUDINARY_API_SECRET");
        if (n == null || n.isBlank() || k == null || k.isBlank() || s == null || s.isBlank()) {
            throw new IllegalStateException("CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET missing.");
        }
        return new Cloud(n, k, s);
    }

    static String basicAuth(Cloud c) {
        return "Basic " + Base64.getEncoder().encodeToString((c.key() + ":" + c.secret()).getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("================================================================================");
        System.out.println("  ONESHOP V3 - PHASE 1: CONTROLLED CLOUDINARY CLEANUP & VERIFICATION");
        System.out.println("================================================================================");

        Path toDeleteFile = Path.of("backup/cloudinary_product_public_ids_to_delete.txt");
        if (!Files.exists(toDeleteFile)) {
            throw new IllegalStateException("File not found: " + toDeleteFile);
        }

        List<String> toDeleteIds = Files.readAllLines(toDeleteFile, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        System.out.println(">>> [1/5] Validating deletion target list: " + toDeleteIds.size() + " IDs...");
        for (String id : toDeleteIds) {
            if (!id.startsWith("oneshop/seed/products/")) {
                throw new IllegalStateException("SAFETY ABORT: ID does not match product prefix: " + id);
            }
            if (id.contains("brand") || id.contains("category")) {
                throw new IllegalStateException("SAFETY ABORT: Brand or Category detected in deletion list: " + id);
            }
        }
        System.out.println("    Safety check PASSED: 100% of targets are strictly under 'oneshop/seed/products/'.");

        Cloud c = loadCloud();
        System.out.println(">>> Cloudinary credentials loaded safely for cloud: " + c.name());

        // Delete in batches of up to 100
        System.out.println(">>> [2/5] Executing controlled deletion via Cloudinary Admin API...");
        int batchSize = 100;
        int totalSuccess = 0;
        int totalNotFound = 0;
        int totalFailed = 0;

        List<String> deletionLogRows = new ArrayList<>();
        deletionLogRows.add("public_id,result_status,cloudinary_response,timestamp");

        for (int i = 0; i < toDeleteIds.size(); i += batchSize) {
            List<String> batch = toDeleteIds.subList(i, Math.min(toDeleteIds.size(), i + batchSize));
            System.out.println("    Processing batch " + (i / batchSize + 1) + " (" + batch.size() + " items)...");

            StringBuilder query = new StringBuilder("invalidate=true");
            for (String id : batch) {
                query.append("&public_ids[]=").append(URLEncoder.encode(id, StandardCharsets.UTF_8));
            }

            URI uri = URI.create("https://api.cloudinary.com/v1_1/" + c.name() + "/resources/image/upload?" + query);

            HttpResponse<String> resp = null;
            int retries = 3;
            while (retries > 0) {
                try {
                    HttpRequest req = HttpRequest.newBuilder(uri)
                            .header("Authorization", basicAuth(c))
                            .DELETE()
                            .build();
                    resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (resp.statusCode() == 200) {
                        break;
                    } else {
                        System.err.println("    HTTP " + resp.statusCode() + " received: " + resp.body());
                        retries--;
                        if (retries > 0) Thread.sleep(2000);
                    }
                } catch (Exception ex) {
                    System.err.println("    Network error: " + ex.getMessage());
                    retries--;
                    if (retries > 0) Thread.sleep(2000);
                }
            }

            if (resp == null || resp.statusCode() != 200) {
                throw new IOException("Batch deletion failed with status " + (resp != null ? resp.statusCode() : "null"));
            }

            String body = resp.body();
            String now = Instant.now().toString();

            // Cloudinary returns: {"deleted":{"oneshop/...":"deleted","oneshop/...":"not_found"},"partial":false}
            for (String id : batch) {
                String pattern = "\"" + Pattern.quote(id) + "\"\\s*:\\s*\"([^\"]+)\"";
                Matcher matcher = Pattern.compile(pattern).matcher(body);
                if (matcher.find()) {
                    String status = matcher.group(1);
                    if ("deleted".equalsIgnoreCase(status) || "not_found".equalsIgnoreCase(status)) {
                        totalSuccess++;
                        deletionLogRows.add(id + ",SUCCESS," + status + "," + now);
                    } else {
                        totalFailed++;
                        deletionLogRows.add(id + ",FAILED," + status + "," + now);
                    }
                } else {
                    totalFailed++;
                    deletionLogRows.add(id + ",UNKNOWN,missing_in_response," + now);
                }
            }
        }

        Path logPath = Path.of("backup/cloudinary_deletion_log_20261013.csv");
        Files.write(logPath, deletionLogRows, StandardCharsets.UTF_8);
        System.out.println(">>> Deletion complete: " + totalSuccess + " confirmed deleted/not_found, " + totalFailed + " failed.");
        System.out.println("    Detailed log written to: " + logPath);

        // Verification of Kept Assets
        System.out.println(">>> [3/5] Verifying KEPT assets (Brands & Categories)...");
        Path brandKeepCsv = Path.of("backup/cloudinary_brand_logos_to_KEEP_20261012_235928.csv");
        Path catKeepCsv = Path.of("backup/cloudinary_category_images_to_KEEP_20261012_235928.csv");

        List<String> keptLogRows = new ArrayList<>();
        keptLogRows.add("asset_type,identifier,public_id,secure_url,http_status,status_result,timestamp");

        int brandsChecked = 0, brandsOk = 0;
        int catsChecked = 0, catsOk = 0;

        if (Files.exists(brandKeepCsv)) {
            List<String> lines = readLinesSafe(brandKeepCsv);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).replaceAll("^\"|\"$", "").trim();
                if (line.isEmpty() || line.startsWith("-")) continue;
                String[] parts = line.split("\",\"|,\"|,\",|,");
                if (parts.length >= 4) {
                    String id = parts[0].replaceAll("\"", "").trim();
                    String slug = parts[1].replaceAll("\"", "").trim();
                    String pid = parts[2].replaceAll("\"", "").trim();
                    String url = parts[3].replaceAll("\"", "").trim();
                    if (!url.startsWith("http")) continue;
                    brandsChecked++;
                    int code = checkUrl(url);
                    boolean ok = (code == 200);
                    if (ok) brandsOk++;
                    keptLogRows.add("BRAND," + slug + "," + pid + "," + url + "," + code + "," + (ok ? "INTACT" : "FAILED") + "," + Instant.now());
                }
            }
        }

        if (Files.exists(catKeepCsv)) {
            List<String> lines = readLinesSafe(catKeepCsv);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).replaceAll("^\"|\"$", "").trim();
                if (line.isEmpty() || line.startsWith("-")) continue;
                String[] parts = line.split("\",\"|,\"|,\",|,");
                if (parts.length >= 4) {
                    String id = parts[0].replaceAll("\"", "").trim();
                    String slug = parts[1].replaceAll("\"", "").trim();
                    String pid = parts[2].replaceAll("\"", "").trim();
                    String url = parts[3].replaceAll("\"", "").trim();
                    if (!url.startsWith("http")) continue;
                    catsChecked++;
                    int code = checkUrl(url);
                    boolean ok = (code == 200);
                    if (ok) catsOk++;
                    keptLogRows.add("CATEGORY," + slug + "," + pid + "," + url + "," + code + "," + (ok ? "INTACT" : "FAILED") + "," + Instant.now());
                }
            }
        }

        Path keptLogPath = Path.of("backup/cloudinary_kept_assets_verification_20261013.csv");
        Files.write(keptLogPath, keptLogRows, StandardCharsets.UTF_8);

        System.out.println("    Brands verified: " + brandsOk + "/" + brandsChecked + " intact (HTTP 200).");
        System.out.println("    Categories verified: " + catsOk + "/" + catsChecked + " intact (HTTP 200).");

        if (brandsOk != brandsChecked || catsOk != catsChecked) {
            throw new IllegalStateException("CRITICAL ALERT: Some kept assets were damaged or inaccessible!");
        }

        // Updating image_manifest.csv
        System.out.println(">>> [4/5] Updating database/seed_large/images/image_manifest.csv...");
        Path manifestPath = Path.of("database/seed_large/images/image_manifest.csv");
        if (Files.exists(manifestPath)) {
            Files.copy(manifestPath, Path.of("backup/image_manifest_v2_backup.csv"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            List<String> mlines = Files.readAllLines(manifestPath, StandardCharsets.UTF_8);
            List<String> newMlines = new ArrayList<>();
            newMlines.add(mlines.get(0));
            int preserved = 0;
            for (int i = 1; i < mlines.size(); i++) {
                String line = mlines.get(i);
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String pid = parts[1];
                    if (!toDeleteIds.contains(pid)) {
                        newMlines.add(line);
                        preserved++;
                    }
                }
            }
            Files.write(manifestPath, newMlines, StandardCharsets.UTF_8);
            System.out.println("    Updated image_manifest.csv: retained " + preserved + " assets (all brands & categories).");
        }

        System.out.println(">>> [5/5] PHASE 1 COMPLETED SUCCESSFULLY!");
        System.out.println("================================================================================");
    }

    private static List<String> readLinesSafe(Path path) throws IOException {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return Files.readAllLines(path, StandardCharsets.UTF_16);
        }
    }

    private static int checkUrl(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "OneShopAudit/1.0")
                    .GET()
                    .build();
            HttpResponse<Void> resp = HTTP.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode();
        } catch (Exception e) {
            return -1;
        }
    }
}

package com.oneshop;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SeedCategoryImagesIntegrityTest {

    private static final Pattern STATIC_IMAGE_PATTERN = Pattern.compile("'/images/([^']+)'");

    @Test
    void allStaticCategoryImagesInSeedFilesExistOnDisk() throws IOException {
        Path seedSqlPath = Path.of("database/04_seed_data.sql");

        assertThat(seedSqlPath).exists();

        checkStaticImagesInFile(seedSqlPath);
    }

    /**
     * The large seed (database/seed_large) moves the three category images of 04_seed_data.sql from static files to
     * Cloudinary. The static files stay in the repo (04 still uses them) but the large seed must not point at them.
     */
    @Test
    void largeSeedCategoriesUseCloudinaryAndStaticSourcesStillExist() throws IOException {
        Path reference = Path.of("database/04_seed_data.sql");
        Path plan = Path.of("database/seed_large/images/image_plan.csv");
        Path manifest = Path.of("database/seed_large/images/image_manifest.csv");
        assertThat(reference).exists();
        assertThat(plan).exists();

        String sql = Files.readString(reference);
        // the base data still names the static files; the large-seed block (after BEGIN LARGE SEED) must not
        int large = sql.indexOf("BEGIN LARGE SEED");
        assertThat(large).as("large seed block present in 04_seed_data.sql").isPositive();
        assertThat(sql.substring(large)).as("large seed must not reference static category images").doesNotContain("'/images/");

        // the three static sources that are re-uploaded to Cloudinary exist on disk
        String planText = Files.readString(plan);
        for (String slug : new String[]{"cham-soc-da", "trang-diem", "cham-soc-toc"}) {
            Matcher m = Pattern.compile("(?m)^category:" + slug + ",static:([^,]+),").matcher(planText);
            assertThat(m.find()).as("plan row for category " + slug).isTrue();
            assertThat(new File("src/main/resources/static/images/site", m.group(1))).as(m.group(1)).exists().isFile();
        }

        // once the images are uploaded, every category of the large seed must be a Cloudinary https URL
        if (Files.exists(manifest)) {
            for (String line : Files.readAllLines(manifest)) {
                if (line.startsWith("category:")) {
                    String url = line.split(",", -1)[2];
                    assertThat(url).startsWith("https://res.cloudinary.com/");
                    assertThat(sql).contains(url);
                }
            }
        }
    }

    private void checkStaticImagesInFile(Path sqlFilePath) throws IOException {
        String content = Files.readString(sqlFilePath);
        Matcher matcher = STATIC_IMAGE_PATTERN.matcher(content);

        int count = 0;
        while (matcher.find()) {
            count++;
            String relativeStaticPath = matcher.group(1);
            File diskFile = new File("src/main/resources/static/images", relativeStaticPath);
            assertThat(diskFile)
                    .as("File referenced in %s must exist: %s", sqlFilePath.getFileName(), diskFile.getPath())
                    .exists()
                    .isFile();
        }
        assertThat(count).as("At least one static image reference found in %s", sqlFilePath.getFileName()).isGreaterThan(0);
    }
}


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
        Path optionalSqlPath = Path.of("database/optional_set_category_images.sql");

        assertThat(seedSqlPath).exists();
        assertThat(optionalSqlPath).exists();

        checkStaticImagesInFile(seedSqlPath);
        checkStaticImagesInFile(optionalSqlPath);
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


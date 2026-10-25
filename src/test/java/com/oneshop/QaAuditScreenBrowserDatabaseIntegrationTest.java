package com.oneshop;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** Additional real-browser screen/navigation/modal/validation coverage; no successful business mutation. */
class QaAuditScreenBrowserDatabaseIntegrationTest extends V3RegressionTestSupport {
    @Test void roleScreensAndSmallUiActions() throws Exception {
        Path dir=QaAuditEvidence.directory("screens").toAbsolutePath();Files.createDirectories(dir);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        Path config=dir.resolve("config.json");json.writeValue(config.toFile(), Map.of("port",port,"output",dir.toString(),
                "chrome","C:/Program Files/Google/Chrome/Application/chrome.exe"));
        Process browser=new ProcessBuilder("node","scripts/qa-audit/screens.mjs",config.toString())
                .redirectErrorStream(true).redirectOutput(dir.resolve("node.log").toFile()).start();
        try {
            assertThat(browser.waitFor(300,TimeUnit.SECONDS)).as("Chrome screen audit finished").isTrue();
            assertThat(browser.exitValue()).as("see target/qa-audit/evidence/screens/results.json and node.log").isZero();
        } finally {if(browser.isAlive())browser.destroyForcibly();}
    }
}

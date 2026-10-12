package com.oneshop.seed;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * database/rebuild_dev_db.ps1 must refuse before touching any database when the target is not local or the
 * confirmation is missing. Every case is refused early or uses -CheckOnly, so no SQL Server is contacted.
 */
class RebuildScriptGuardTest {

    private static final Path SCRIPT = Path.of("database/rebuild_dev_db.ps1").toAbsolutePath();

    @BeforeAll
    static void windowsPowerShellOnly() {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"), "PowerShell script test runs on Windows");
        Assumptions.assumeTrue(Files.exists(SCRIPT));
    }

    private record Result(int exit, String output) { }

    private static Result run(String confirm, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", SCRIPT.toString()));
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().remove("REBUILD_CONFIRM");
        if (confirm != null) pb.environment().put("REBUILD_CONFIRM", confirm);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return new Result(p.exitValue(), out);
    }

    @Test
    void refusesARemoteServer() throws Exception {
        Result r = run("oneshop", "-Server", "prod-sql.example.com", "-Database", "oneshop", "-CheckOnly");
        assertThat(r.exit()).isEqualTo(10);
        assertThat(r.output()).contains("not local");
    }

    @Test
    void refusesCloudAndLanAddressesEvenWithAPort() throws Exception {
        assertThat(run("oneshop", "-Server", "myserver.database.windows.net,1433", "-Database", "oneshop", "-CheckOnly").exit()).isEqualTo(10);
        assertThat(run("oneshop", "-Server", "10.0.0.5", "-Database", "oneshop", "-CheckOnly").exit()).isEqualTo(10);
    }

    @Test
    void refusesWithoutOrWithWrongConfirmation() throws Exception {
        assertThat(run(null, "-Server", "localhost", "-Database", "oneshop", "-CheckOnly").exit()).isEqualTo(13);
        Result wrong = run("someotherdb", "-Server", "localhost", "-Database", "oneshop", "-CheckOnly");
        assertThat(wrong.exit()).isEqualTo(13);
        assertThat(wrong.output()).contains("REBUILD_CONFIRM");
    }

    @Test
    void refusesSystemDatabasesAndOddNames() throws Exception {
        assertThat(run("master", "-Server", "localhost", "-Database", "master", "-CheckOnly").exit()).isEqualTo(12);
        assertThat(run("a;drop", "-Server", "localhost", "-Database", "a;drop", "-CheckOnly").exit()).isEqualTo(11);
    }

    @Test
    void acceptsLocalTargetsWhenConfirmed() throws Exception {
        for (String server : List.of("localhost", "127.0.0.1", "(local)", ".", ".\\SQLEXPRESS", "localhost,1433")) {
            Result r = run("oneshop_test", "-Server", server, "-Database", "oneshop_test", "-CheckOnly");
            assertThat(r.exit()).as(server + ": " + r.output()).isZero();
            assertThat(r.output()).contains("Target database: oneshop_test").contains("nothing was touched");
        }
    }
}

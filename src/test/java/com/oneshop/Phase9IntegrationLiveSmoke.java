package com.oneshop;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** No Spring test context: exercises the final packaged JAR over HTTP and the real SQL Server over JDBC. */
public final class Phase9IntegrationLiveSmoke {
    private static String value(Properties config, String name, String fallback) { return System.getenv().getOrDefault(name, config.getProperty(name, fallback)); }
    public static void main(String[] args) throws Exception {
        Properties config = new Properties();
        if (Files.exists(Path.of(".env"))) try (Reader r = Files.newBufferedReader(Path.of(".env"), StandardCharsets.UTF_8)) { config.load(r); }
        String url = "jdbc:sqlserver://" + value(config, "DB_HOST", "localhost") + ":" + value(config, "DB_PORT", "1433")
                + ";databaseName=" + value(config, "DB_NAME", "oneshop") + ";encrypt=true;trustServerCertificate=" + value(config, "DB_TRUST_SERVER_CERTIFICATE", "false");
        var source = new DriverManagerDataSource(url, value(config, "DB_USERNAME", ""), value(config, "DB_PASSWORD", ""));
        source.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        var s = new Phase9IntegrationScenario(new JdbcTemplate(source), args.length == 0 ? "http://localhost:18081" : args[0]);
        var before = s.snapshot(); var scope = s.scope();
        s.happy(false); System.out.println("TC-15 full three-store HTTP lifecycle / isolation / receipts / timelines / inventory: PASS");
        s.happy(true); System.out.println("Full fulfillment after catalog name/price changes / snapshots: PASS");
        s.mixedFailure(); System.out.println("Mixed failed ONLINE / only target restore / sibling completion: PASS");
        s.security(); System.out.println("Customer ownership / all Staff cross-store combinations / code visibility: PASS");
        s.csrfAndRoles(); System.out.println("CSRF cookie+Bearer+encoded paths / roles / GET / cross-flow: PASS");
        s.multiAssignment(); System.out.println("Multiple ACTIVE assignments / inactive scope / trusted actor: PASS");
        s.invalidCombination("DELIVERY", "PAY_AT_STORE"); s.invalidCombination("STORE_PICKUP", "COD");
        System.out.println("Invalid pairings business+SQL CHECK: PASS");
        Phase9IntegrationScenario.check(s.snapshot().equals(before) && s.scope().equals(scope), "final exact cleanup");
        System.out.println("Cleanup exact rows of nine business tables + account/store/assignment metadata: PASS");
        before.forEach((name, rows) -> System.out.println(name + ": " + rows.size() + " -> " + s.rows(name).size()));
    }
}

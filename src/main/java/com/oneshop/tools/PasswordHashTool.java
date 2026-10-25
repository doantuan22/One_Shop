package com.oneshop.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Command line helper for operators: prints the BCrypt hash the application's own {@code PasswordEncoder} accepts, so a
 * database script can be given a hash instead of a clear text password. The password is read from the
 * {@code ONESHOP_PASSWORD} environment variable or, when that is not set, from the first line of standard input;
 * it is never taken from the command line (shell history) and never printed.
 */
public final class PasswordHashTool {
    private PasswordHashTool() {
    }

    public static void main(String[] args) throws Exception {
        String password = System.getenv("ONESHOP_PASSWORD");
        if (password == null || password.isEmpty()) {
            password = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            System.err.println("The password must be 8 to 72 characters (set ONESHOP_PASSWORD or pipe it to stdin).");
            System.exit(2);
        }
        System.out.println(new BCryptPasswordEncoder().encode(password));
    }
}

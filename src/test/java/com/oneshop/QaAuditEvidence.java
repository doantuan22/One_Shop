package com.oneshop;

import java.nio.file.Path;

/** Keep original audit evidence immutable when verifying a later fix. */
final class QaAuditEvidence {
    private QaAuditEvidence() { }
    static Path directory(String name) {
        return Path.of(System.getProperty("oneshop.qa.evidence-dir", "target/qa-audit/evidence")).resolve(name);
    }
}

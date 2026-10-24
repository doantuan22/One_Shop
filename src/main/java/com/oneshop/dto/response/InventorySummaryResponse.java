package com.oneshop.dto.response;

/**
 * Totals above the chain inventory table (admin/inventory). They are live SQL counts for the chosen
 * breadth — never hard-coded and never a synthetic KPI. {@code lowThreshold} echoes the Staff
 * operations threshold so both consoles label "sắp hết" with the same rule.
 */
public record InventorySummaryResponse(long total, long low, long out, int lowThreshold) {}

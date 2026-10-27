package com.oneshop.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Hibernate validates columns, but does not validate DEC-26 filtered uniqueness. Read-only startup prerequisite. */
@Component
@Profile("!test")
public class V3SchemaCompatibilityVerifier implements SmartInitializingSingleton {
    record KeyColumn(String name, boolean unique, boolean disabled, String filter, int ordinal, String column) { }
    private final JdbcTemplate jdbc;

    public V3SchemaCompatibilityVerifier(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @Override
    public void afterSingletonsInstantiated() {
        List<KeyColumn> keys=jdbc.query("""
                select i.name,i.is_unique,i.is_disabled,i.filter_definition,ic.key_ordinal,c.name
                from sys.indexes i join sys.index_columns ic on ic.object_id=i.object_id and ic.index_id=i.index_id
                join sys.columns c on c.object_id=ic.object_id and c.column_id=ic.column_id
                where i.object_id=object_id(N'dbo.reviews') and ic.key_ordinal>0
                order by i.name,ic.key_ordinal
                """,(rs,n)->new KeyColumn(rs.getString(1),rs.getBoolean(2),rs.getBoolean(3),
                rs.getString(4),rs.getInt(5),rs.getString(6)));
        requireCompatible(keys);
        requireRefundLedger();
        requireReturnFlow();
        requirePromotions();
        requirePayos();
    }

    /** payOS: provider columns on payments, the webhook journal, and the keys that make a double collection impossible. */
    private void requirePayos() {
        Integer columns = jdbc.queryForObject("select count(*) from sys.columns where object_id=object_id(N'dbo.payments') and name in "
                + "(N'provider',N'provider_order_code',N'provider_link_id',N'checkout_url',N'provider_status',"
                + "N'provider_amount_paid',N'provider_reference',N'provider_paid_at',N'expires_at',N'last_synced_at')", Integer.class);
        Integer refundColumns = jdbc.queryForObject("select count(*) from sys.columns where object_id=object_id(N'dbo.payment_refunds') and name in "
                + "(N'evidence_reference',N'processed_by_user_id',N'processed_at',N'processed_note')", Integer.class);
        Integer journal = jdbc.queryForObject("select count(*) from sys.tables where name=N'payment_provider_events'", Integer.class);
        Integer keys = jdbc.queryForObject("select count(*) from sys.indexes where is_unique=1 and has_filter=1 and is_disabled=0 and "
                + "((object_id=object_id(N'dbo.payments') and name in (N'UX_payments_provider_order_code',N'UX_payments_provider_link',N'UX_payments_one_pending_payos'))"
                + " or (object_id=object_id(N'dbo.payment_refunds') and name=N'UX_payment_refunds_evidence'))", Integer.class);
        if (columns == null || columns != 10 || refundColumns == null || refundColumns != 4 || journal == null || journal != 1
                || keys == null || keys != 4) {
            throw new IllegalStateException("V3 schema prerequisite missing: payOS payments. "
                    + "Stop startup; back up and obtain approval for database/migrations/V009_payos_payments.sql (an existing database only; a fresh install uses database/schema/01_schema.sql). "
                    + "No migration was executed automatically.");
        }
    }

    /** Promotion codes: the two tables and the discount snapshot columns of orders / order_items. */
    private void requirePromotions() {
        Integer tables = jdbc.queryForObject("select count(*) from sys.tables where name in "
                + "(N'promotions',N'promotion_usages')", Integer.class);
        Integer columns = jdbc.queryForObject("select count(*) from sys.columns where "
                + "(object_id=object_id(N'dbo.orders') and name in (N'discount_amount',N'promotion_code')) or "
                + "(object_id=object_id(N'dbo.order_items') and name=N'discount_amount')", Integer.class);
        if (tables == null || tables != 2 || columns == null || columns != 3) {
            throw new IllegalStateException("V3 schema prerequisite missing: promotion codes. "
                    + "Stop startup; back up and obtain approval for database/migrations/V004_promotions.sql (an existing database only; a fresh install uses database/schema/01_schema.sql). "
                    + "No migration was executed automatically.");
        }
    }

    /** Post-delivery returns: the three return tables and the RETURN ledger key (one refund per return). */
    private void requireReturnFlow() {
        Integer tables = jdbc.queryForObject("select count(*) from sys.tables where name in "
                + "(N'order_returns',N'order_return_images',N'order_return_items')", Integer.class);
        Integer ledgerKey = jdbc.queryForObject("select count(*) from sys.indexes where object_id=object_id(N'dbo.payment_refunds')"
                + " and name=N'UX_payment_refunds_return' and is_unique=1 and has_filter=1 and is_disabled=0", Integer.class);
        if (tables == null || tables != 3 || ledgerKey == null || ledgerKey != 1) {
            throw new IllegalStateException("V3 schema prerequisite missing: post-delivery return flow. "
                    + "Stop startup; back up and obtain approval for database/migrations/V003_order_returns.sql (an existing database only; a fresh install uses database/schema/01_schema.sql). "
                    + "No migration was executed automatically.");
        }
    }

    /** OPEN-10.1: the refund ledger and its filtered unique keys (they are what makes a double refund impossible). */
    private void requireRefundLedger() {
        Integer present = jdbc.queryForObject("select count(*) from sys.indexes where object_id=object_id(N'dbo.payment_refunds')"
                + " and name in (N'UX_payment_refunds_goods',N'UX_payment_refunds_shipping',N'UX_payment_refunds_whole')"
                + " and is_unique=1 and has_filter=1 and is_disabled=0", Integer.class);
        if (present == null || present != 3) {
            throw new IllegalStateException("V3 schema prerequisite missing: OPEN-10.1 payment_refunds ledger. "
                    + "Stop startup; back up and obtain approval for database/migrations/V002_payment_refunds.sql (an existing database only; a fresh install uses database/schema/01_schema.sql). "
                    + "No migration was executed automatically.");
        }
    }

    static void requireCompatible(List<KeyColumn> keys) {
        if (keys.stream().anyMatch(k->k.name().equals("UQ_reviews_user_product"))
                || !matches(keys,"UX_reviews_order_item","ORDER_ITEM_IDISNOTNULL",List.of("order_item_id"))
                || !matches(keys,"UX_reviews_legacy_user_product","ORDER_ITEM_IDISNULL",List.of("user_id","product_id"))) {
            throw new IllegalStateException("V3 schema prerequisite missing: DEC-26 Review filtered unique indexes. "
                    + "Stop startup; back up and obtain approval for database/migrations/V001_review_uniqueness.sql (an existing database only; a fresh install uses database/schema/01_schema.sql). "
                    + "No migration was executed automatically.");
        }
    }

    private static boolean matches(List<KeyColumn> all,String name,String filter,List<String> columns) {
        List<KeyColumn> keys=all.stream().filter(k->k.name().equals(name)).toList();
        if(keys.size()!=columns.size())return false;
        for(int n=0;n<keys.size();n++) {
            KeyColumn key=keys.get(n);
            String predicate=key.filter()==null?"":key.filter().replaceAll("[\\[\\]()\\s]", "").toUpperCase(Locale.ROOT);
            if(!key.unique()||key.disabled()||!predicate.equals(filter)||key.ordinal()!=n+1
                    ||!key.column().equals(columns.get(n)))return false;
        }
        return true;
    }
}

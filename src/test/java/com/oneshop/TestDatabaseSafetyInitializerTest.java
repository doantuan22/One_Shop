package com.oneshop;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.*;

class TestDatabaseSafetyInitializerTest {
    @ParameterizedTest
    @ValueSource(strings={"jdbc:sqlserver://localhost;databaseName=oneshop", "jdbc:sqlserver://localhost",
            "jdbc:sqlserver://localhost;databaseName=other_test", "jdbc:sqlserver://localhost;databaseName=oneshop_test;databaseName=oneshop",
            "jdbc:sqlserver://localhost;databaseName={oneshop};password={hidden}"})
    void refusesOperationalMissingOrAmbiguousTargets(String url) {
        assertThatThrownBy(()->TestDatabaseSafetyInitializer.requireTestUrl(url))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("hidden");
    }

    @ParameterizedTest
    @ValueSource(strings={"jdbc:sqlserver://localhost;databaseName=oneshop_test",
            "jdbc:sqlserver://localhost;databaseName={oneshop_v3_audit_test}",
            "jdbc:sqlserver://localhost;databaseName=ONESHOP_V3_TEST"})
    void allowsOnlyExplicitOneShopTestTargets(String url) {
        assertThatCode(()->TestDatabaseSafetyInitializer.requireTestUrl(url)).doesNotThrowAnyException();
    }

    @Test void refusesADestructiveHibernateOverrideBeforeBeanCreation() {
        try(var context=new GenericApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("spring.datasource.url", "jdbc:sqlserver://localhost;databaseName=oneshop_test")
                    .withProperty("spring.jpa.hibernate.ddl-auto","update"));
            assertThatThrownBy(()->new TestDatabaseSafetyInitializer().initialize(context)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void refusesAHikariOverridePointingAtTheOperationalDatabase() {
        try(var context=new GenericApplicationContext()) {
            context.setEnvironment(new MockEnvironment().withProperty("spring.datasource.url", "jdbc:sqlserver://localhost;databaseName=oneshop_test")
                    .withProperty("spring.datasource.hikari.jdbc-url","jdbc:sqlserver://localhost;databaseName=oneshop"));
            assertThatThrownBy(()->new TestDatabaseSafetyInitializer().initialize(context)).isInstanceOf(IllegalStateException.class);
        }
    }
}

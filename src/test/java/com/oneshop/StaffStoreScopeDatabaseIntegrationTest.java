package com.oneshop;

import com.oneshop.exception.ResourceNotFoundException;
import com.oneshop.service.StaffStoreScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

/** Real JWT/Security/Service/repositories/SQL Server. Probe routes are imported only in this test context. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
@Import(StaffStoreScopeDatabaseIntegrationTest.ScopeProbe.class)
class StaffStoreScopeDatabaseIntegrationTest {
    private static final String STAFF = "staff.thuduc@oneshop.vn";
    private static final String PEER = "staff.govap@oneshop.vn";
    private static final String ROOT = "/api/staff/scope-test";
    @Autowired StaffStoreScopeService scope;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;
    private Phase9IntegrationScenario verifier;
    private Map<String, List<Map<String, Object>>> before, scopeBefore;

    @BeforeEach void remember() {
        SecurityContextHolder.clearContext();
        verifier = new Phase9IntegrationScenario(jdbc, "http://localhost:" + port);
        before = verifier.snapshot(); scopeBefore = verifier.scope();
    }
    @AfterEach void unchanged() {
        SecurityContextHolder.clearContext();
        assertThat(verifier.snapshot()).as("Scope foundation reads never mutate business rows").isEqualTo(before);
        assertThat(verifier.scope()).as("Security fixtures restored including metadata/timestamps").isEqualTo(scopeBefore);
    }
    private <T> T as(String email, String role, Supplier<T> read) {
        var previous = SecurityContextHolder.getContext(); var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
        try { return read.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
    private long staffId() { return jdbc.queryForObject("select user_id from dbo.users where email=?", Long.class, STAFF); }
    private long product(long store) { return jdbc.queryForObject("select min(store_product_id) from dbo.store_products where store_id=?", Long.class, store); }

    @Test void activeStaffResolvesActualAssignmentAndStore() {
        assertThat(as(STAFF, "STAFF", scope::getAssignedStoreIds)).containsExactly(1L);
        assertThat(as(PEER, "STAFF", scope::getAssignedStoreIds)).containsExactly(2L);
        assertThat(as(STAFF, "STAFF", () -> scope.requireAssignedStore(1L)).id()).isEqualTo(1L);
        assertThatThrownBy(() -> as(STAFF, "STAFF", () -> scope.requireAssignedStore(2L))).isInstanceOf(AccessDeniedException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"UNKNOWN_STAFF", "INACTIVE_ASSIGNMENT", "INACTIVE_STORE", "INACTIVE_USER", "WRONG_DATABASE_ROLE"})
    void absentOrInvalidAssignmentDeniesServiceAndRealStaffHttp(String reason) throws Exception {
        if (reason.equals("UNKNOWN_STAFF")) {
            assertThatThrownBy(() -> as("unassigned@oneshop.test", "STAFF", scope::getAssignedStoreIds)).isInstanceOf(AccessDeniedException.class);
            return;
        }
        // Get a valid JWT first, then revoke DB scope: the same JWT must not preserve yesterday's assignments.
        String cookie = verifier.account(STAFF); long user = staffId();
        long role = jdbc.queryForObject("select role_id from dbo.users where user_id=?", Long.class, user);
        var assignment = jdbc.queryForMap("select * from dbo.staff_store_assignments where user_id=? and store_id=1", user);
        var store = jdbc.queryForMap("select * from dbo.stores where store_id=1");
        var account = jdbc.queryForMap("select status,updated_at from dbo.users where user_id=?", user);
        try {
            switch (reason) {
                case "INACTIVE_ASSIGNMENT" -> jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where user_id=?", user);
                case "INACTIVE_STORE" -> jdbc.update("update dbo.stores set status='INACTIVE' where store_id=1");
                case "INACTIVE_USER" -> jdbc.update("update dbo.users set status='INACTIVE' where user_id=?", user);
                case "WRONG_DATABASE_ROLE" -> jdbc.update("update dbo.users set role_id=(select role_id from dbo.roles where name='CUSTOMER') where user_id=?", user);
                default -> throw new AssertionError(reason);
            }
            assertThatThrownBy(() -> as(STAFF, "STAFF", scope::getAssignedStoreIds)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> as(STAFF, "STAFF", () -> scope.requireOrder(1L))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> as(STAFF, "STAFF", () -> scope.requireStoreProduct(product(1)))).isInstanceOf(AccessDeniedException.class);
            // An inactive account becomes unauthenticated; other invalid scopes receive 403.
            assertThat(verifier.get(ROOT + "/scope?store_id=1&staffEmail=" + PEER, cookie).statusCode()).isEqualTo(reason.equals("INACTIVE_USER") ? 401 : 403);
            if (reason.equals("INACTIVE_ASSIGNMENT")) assertThat(verifier.get("/staff/orders/delivery?store_id=1", cookie).statusCode()).isEqualTo(403);
        } finally {
            jdbc.update("update dbo.staff_store_assignments set status=? where assignment_id=?", assignment.get("status"), assignment.get("assignment_id"));
            jdbc.update("update dbo.stores set status=?,updated_at=? where store_id=1", store.get("status"), store.get("updated_at"));
            jdbc.update("update dbo.users set role_id=?,status=?,updated_at=? where user_id=?", role, account.get("status"), account.get("updated_at"), user);
        }
    }
    @Test void unauthenticatedServiceCallIsDeniedBeforeResourceLookup() {
        assertThatThrownBy(scope::getAssignedStores).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> scope.requireOrder(1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> scope.requireStoreProduct(product(1))).isInstanceOf(AccessDeniedException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"CUSTOMER", "ADMIN"})
    void nonStaffCannotUseFoundationEvenWithAnAssignedStaffEmail(String role) {
        assertThatThrownBy(() -> as(STAFF, role, scope::getAssignedStoreIds)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void ownOrderAndStoreProductAreAllowedIncludingInventoryScope() {
        assertThat(as(STAFF, "STAFF", () -> scope.requireOrder(1L)).getStore().getId()).isEqualTo(1L);
        var stock = as(STAFF, "STAFF", () -> scope.requireStoreProduct(product(1)));
        assertThat(stock.getStore().getId()).isEqualTo(1L);
        assertThat(stock.getProduct().getSku()).isNotBlank();
        assertThat(as(STAFF, "STAFF", () -> scope.requireAssignedStore(stock.getStore().getId())).id()).isEqualTo(1L);
    }
    @Test void crossStoreAndUnknownResourcesAreIndistinguishablyDenied() {
        for (long id : List.of(2L, Long.MAX_VALUE)) assertThatThrownBy(() -> as(STAFF, "STAFF", () -> scope.requireOrder(id))).isInstanceOf(ResourceNotFoundException.class);
        for (long id : List.of(product(2), Long.MAX_VALUE)) assertThatThrownBy(() -> as(STAFF, "STAFF", () -> scope.requireStoreProduct(id))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> as(STAFF, "STAFF", () -> scope.requireOrder(null))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> as(STAFF, "STAFF", () -> scope.requireStoreProduct(null))).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void multipleAssignmentsPreserveOnlyActiveScopeAndResolveFreshAfterRevocation() {
        long user = staffId(), max = jdbc.queryForObject("select max(assignment_id) from dbo.staff_store_assignments", Long.class);
        try {
            jdbc.update("insert into dbo.staff_store_assignments(user_id,store_id,status,assigned_at) values (?,2,'ACTIVE',SYSDATETIME()),(?,4,'INACTIVE',SYSDATETIME())", user, user);
            assertThat(as(STAFF, "STAFF", scope::getAssignedStoreIds)).containsExactlyInAnyOrder(1L, 2L);
            assertThat(as(STAFF, "STAFF", () -> scope.requireOrder(2L)).getStore().getId()).isEqualTo(2L);
            assertThat(as(STAFF, "STAFF", () -> scope.requireStoreProduct(product(2))).getStore().getId()).isEqualTo(2L);
            assertThatThrownBy(() -> as(STAFF, "STAFF", () -> scope.requireStoreProduct(product(4)))).isInstanceOf(ResourceNotFoundException.class);
            jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where assignment_id>? and store_id=2", max);
            assertThat(as(STAFF, "STAFF", scope::getAssignedStoreIds)).containsExactly(1L);
            assertThatThrownBy(() -> as(STAFF, "STAFF", () -> scope.requireOrder(2L))).isInstanceOf(ResourceNotFoundException.class);
        } finally { jdbc.update("delete from dbo.staff_store_assignments where assignment_id>? and user_id=?", max, user); }
    }
    @ParameterizedTest @ValueSource(strings = {"query", "form", "body", "path"})
    void forgingStoreIdOnHttpCannotChangeScopeOrExposePeerStock(String source) throws Exception {
        String cookie = verifier.account(STAFF); long own = product(1), peer = product(2);
        String forged = "store_id=2&storeId=2&userId=3&staffEmail=" + PEER;
        if (source.equals("path")) {
            assertThat(verifier.get(ROOT + "/stores/2/products/" + own, cookie).statusCode()).isEqualTo(403);
            assertThat(verifier.get(ROOT + "/stores/1/products/" + peer, cookie).statusCode()).isEqualTo(404);
            assertThat(verifier.get(ROOT + "/stores/1/products/" + own, cookie).statusCode()).isEqualTo(200);
            return;
        }
        HttpResponse<String> allowed, denied;
        if (source.equals("query")) {
            allowed = verifier.get(ROOT + "/products/" + own + "?" + forged, cookie);
            denied = verifier.get(ROOT + "/products/" + peer + "?store_id=1", cookie);
        } else if (source.equals("form")) {
            allowed = verifier.post(cookie, ROOT + "/products/" + own, forged);
            denied = verifier.post(cookie, ROOT + "/products/" + peer, "store_id=1&staffEmail=" + PEER);
        } else {
            allowed = json(cookie, own, "{\"store_id\":2,\"storeId\":2,\"staffEmail\":\"" + PEER + "\"}");
            denied = json(cookie, peer, "{\"store_id\":1}");
        }
        assertThat(allowed.statusCode()).isEqualTo(200); assertThat(allowed.body()).contains("\"storeId\":1");
        assertThat(denied.statusCode()).isEqualTo(404);
        assertThat(verifier.get(ROOT + "/scope?" + forged, cookie).body()).isEqualTo("[1]");
    }
    private HttpResponse<String> json(String cookie, long id, String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + ROOT + "/products/" + id))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + cookie.substring("ONESHOP_TOKEN=".length()))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void existingOrderUrlsStillEnforceStoreScopeWithForgedRequestStore() throws Exception {
        String cookie = verifier.account(STAFF);
        assertThat(verifier.get("/staff/orders/delivery/1?store_id=2&storeId=2", cookie).statusCode()).isEqualTo(200);
        assertThat(verifier.get("/staff/orders/pickup/2?store_id=1&storeId=1", cookie).statusCode()).isEqualTo(404);
    }

    /** Test-only adapter exercises actual HTTP security/context; adds no production Staff stock API or UI. */
    @TestConfiguration(proxyBeanMethods = false)
    @RestController
    @RequestMapping(ROOT)
    static class ScopeProbe {
        private final StaffStoreScopeService scope;
        ScopeProbe(StaffStoreScopeService scope) { this.scope = scope; }
        @GetMapping("/scope") List<Long> stores() { return scope.getAssignedStoreIds(); }
        @RequestMapping(value = "/products/{id}", method = {RequestMethod.GET, RequestMethod.POST})
        Map<String, Long> product(@PathVariable Long id) {
            var sp = scope.requireStoreProduct(id); return Map.of("storeProductId", sp.getId(), "storeId", sp.getStore().getId());
        }
        @GetMapping("/stores/{storeId}/products/{id}") Map<String, Long> selected(@PathVariable Long storeId, @PathVariable Long id) {
            scope.requireAssignedStore(storeId); return product(id);
        }
        // Production API advice is package-scoped to controller.api; this test-only adapter lives in com.oneshop.
        @ExceptionHandler(ResourceNotFoundException.class)
        @ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
        Map<String, String> notFound(ResourceNotFoundException ex) { return Map.of("message", ex.getMessage()); }
    }
}

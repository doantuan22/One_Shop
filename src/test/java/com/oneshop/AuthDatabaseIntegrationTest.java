package com.oneshop;

import com.oneshop.dto.request.LoginRequest;
import com.oneshop.dto.request.RegisterRequest;
import com.oneshop.dto.response.AuthResponse;
import com.oneshop.dto.response.StoreResponse;
import com.oneshop.dto.response.UserResponse;
import com.oneshop.entity.ActiveStatus;
import com.oneshop.entity.RoleName;
import com.oneshop.entity.StaffStoreAssignment;
import com.oneshop.entity.User;
import com.oneshop.exception.BadRequestException;
import com.oneshop.repository.StaffStoreAssignmentRepository;
import com.oneshop.repository.StoreRepository;
import com.oneshop.repository.UserRepository;
import com.oneshop.service.AuthService;
import com.oneshop.service.StoreService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 5 against the real SQL Server and its seed data ({@code database/04_seed_data.sql}): login of the three roles,
 * registration and the Staff Store scope read from {@code staff_store_assignments}.
 *
 * <p>Skipped without a configured database, like {@link DatabaseFoundationIntegrationTest}. Every test that writes
 * runs in a transaction that is rolled back, so the database is left exactly as it was.
 */
@SpringBootTest(properties = "app.jwt.secret=test-only-secret-test-only-secret-1234567890")
@ActiveProfiles("dev")
@EnabledIf("com.oneshop.DatabaseFoundationIntegrationTest#databaseConfigured")
class AuthDatabaseIntegrationTest {

    private static final String SEED_PASSWORD = "OneShop@123";
    private static final String ADMIN = "admin@oneshop.vn";
    private static final String STAFF_THU_DUC = "staff.thuduc@oneshop.vn";
    private static final String STAFF_GO_VAP = "staff.govap@oneshop.vn";
    private static final String CUSTOMER = "khachhang1@example.com";

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AuthService authService;

    @Autowired
    private StoreService storeService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private StaffStoreAssignmentRepository assignmentRepository;

    private static LoginRequest login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    @Test
    void seedAccountsLogInWithTheirRoleFromTheDatabase() {
        assertThat(authService.login(login(ADMIN, SEED_PASSWORD)).roles()).containsExactly("ROLE_ADMIN");
        assertThat(authService.login(login(STAFF_THU_DUC, SEED_PASSWORD)).roles()).containsExactly("ROLE_STAFF");
        AuthResponse customer = authService.login(login("  KhachHang1@Example.com ", SEED_PASSWORD));
        assertThat(customer.roles()).containsExactly("ROLE_CUSTOMER");
        assertThat(customer.email()).isEqualTo(CUSTOMER);
        assertThat(customer.accessToken()).isNotBlank();
    }

    @Test
    void wrongPasswordAndUnknownAccountAreRejectedTheSameWay() {
        assertThatThrownBy(() -> authService.login(login(ADMIN, "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> authService.login(login("nobody@oneshop.vn", SEED_PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @Transactional
    void inactiveAccountCannotLogIn() {
        User customer = userRepository.findByEmail(CUSTOMER).orElseThrow();
        customer.setStatus(ActiveStatus.INACTIVE);
        entityManager.flush();

        assertThatThrownBy(() -> authService.login(login(CUSTOMER, SEED_PASSWORD)))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    @Transactional
    void registrationStoresACustomerThatCanLogIn() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Khách Thử Nghiệm");
        request.setEmail("Phase5.Test@Example.com");
        request.setPhone("0900000099");
        request.setPassword("Phase5@Test");

        UserResponse created = authService.register(request);
        entityManager.flush();
        entityManager.clear();

        User stored = userRepository.findById(created.id()).orElseThrow();
        assertThat(stored.getEmail()).isEqualTo("phase5.test@example.com");
        assertThat(stored.getRole().getName()).isEqualTo(RoleName.CUSTOMER);
        assertThat(stored.getPasswordHash()).startsWith("$2").doesNotContain("Phase5@Test");
        assertThat(stored.getFullName()).isEqualTo("Khách Thử Nghiệm");
        assertThat(authService.login(login("phase5.test@example.com", "Phase5@Test")).roles())
                .containsExactly("ROLE_CUSTOMER");

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void staffScopeIsExactlyTheStoreOfTheActiveAssignment() {
        List<StoreResponse> thuDuc = storeService.getAssignedStores(STAFF_THU_DUC);
        List<StoreResponse> goVap = storeService.getAssignedStores(STAFF_GO_VAP);

        assertThat(thuDuc).hasSize(1);
        assertThat(goVap).hasSize(1);
        assertThat(thuDuc.get(0).id()).isNotEqualTo(goVap.get(0).id());

        // own Store: allowed. Another Staff's Store or an unknown Store: refused (TC-11 foundation)
        assertThat(storeService.requireAssignedStore(STAFF_THU_DUC, thuDuc.get(0).id())).isEqualTo(thuDuc.get(0));
        assertThatThrownBy(() -> storeService.requireAssignedStore(STAFF_THU_DUC, goVap.get(0).id()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> storeService.requireAssignedStore(STAFF_THU_DUC, -1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void accountsThatAreNotStaffHaveNoStoreScope() {
        assertThat(storeService.getAssignedStores(ADMIN)).isEmpty();
        assertThat(storeService.getAssignedStores(CUSTOMER)).isEmpty();
        assertThat(storeService.getAssignedStores("nobody@oneshop.vn")).isEmpty();
        assertThat(storeService.getAssignedStores(null)).isEmpty();

        Long anyStore = storeRepository.findAll().get(0).getId();
        assertThatThrownBy(() -> storeService.requireAssignedStore(ADMIN, anyStore))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @Transactional
    void inactiveAssignmentGivesNoStoreScope() {
        Long storeId = storeService.getAssignedStores(STAFF_THU_DUC).get(0).id();
        StaffStoreAssignment assignment = assignmentOf(STAFF_THU_DUC);

        assignment.setStatus(ActiveStatus.INACTIVE);
        entityManager.flush();

        assertThat(storeService.getAssignedStores(STAFF_THU_DUC)).isEmpty();
        assertThatThrownBy(() -> storeService.requireAssignedStore(STAFF_THU_DUC, storeId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @Transactional
    void inactiveStoreOrInactiveStaffAccountGivesNoStoreScope() {
        StaffStoreAssignment assignment = assignmentOf(STAFF_THU_DUC);

        assignment.getStore().setStatus(ActiveStatus.INACTIVE);
        entityManager.flush();
        assertThat(storeService.getAssignedStores(STAFF_THU_DUC)).isEmpty();

        assignment.getStore().setStatus(ActiveStatus.ACTIVE);
        assignment.getUser().setStatus(ActiveStatus.INACTIVE);
        entityManager.flush();
        assertThat(storeService.getAssignedStores(STAFF_THU_DUC)).isEmpty();
    }

    private StaffStoreAssignment assignmentOf(String email) {
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        return assignmentRepository.findByUserIdAndStatus(userId, ActiveStatus.ACTIVE).get(0);
    }
}

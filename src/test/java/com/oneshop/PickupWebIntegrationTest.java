package com.oneshop;

import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.exception.*;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import com.oneshop.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickupWebIntegrationTest extends AbstractIntegrationTest {
    private static final String STAFF="staff@oneshop.test",ROOT="/staff/orders/pickup";
    @MockitoBean private PickupFulfillmentService pickup;
    @MockitoBean private CustomerOrderService orders;
    @Autowired private JwtService jwt;
    @Autowired private CustomUserDetailsService users;
    @BeforeEach void scope() {
        when(storeService.getAssignedStores(STAFF)).thenReturn(List.of(new StoreResponse(1L,"TD","OneShop Thủ Đức","1 Đường thử","HCM","Thủ Đức",null,null,true,true,ActiveStatus.ACTIVE)));
    }
    private String cookie(RoleName role) {
        var u=new User();u.setEmail(STAFF);u.setRole(new Role(role));u.setPasswordHash("x");when(userRepository.findByEmail(STAFF)).thenReturn(Optional.of(u));
        return "ONESHOP_TOKEN="+jwt.generateToken(users.loadUserByUsername(STAFF));
    }
    private OrderViewResponse row(OrderStatus state,PickupAction action,String code) {
        return new OrderViewResponse(10L,1L,"OneShop Thủ Đức","1 Đường thử",LocalDateTime.now(),FulfillmentType.STORE_PICKUP,
                PaymentMethod.PAY_AT_STORE,OrderPaymentStatus.UNPAID,state,"Người nhận","0912345678",null,new BigDecimal("175000"),
                state==OrderStatus.READY_FOR_PICKUP?LocalDateTime.now():null,null,code,action);
    }
    private OrderDetailResponse detail(OrderViewResponse row) {
        return new OrderDetailResponse(row,List.of(new OrderItemResponse("Snapshot <script>alert(1)</script>",new BigDecimal("175000"),1,new BigDecimal("175000"))),
                List.of(new OrderHistoryResponse(OrderStatus.CONFIRMED,OrderStatus.PREPARING,"Nhân viên A","Đã chuẩn bị",LocalDateTime.now())),List.of());
    }
    private HttpResponse<String> postAs(String cookie,String path,String body) throws Exception {
        var login=get("/login");Matcher csrf=Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(login.body());assertThat(csrf.find()).isTrue();
        String token=login.headers().allValues("Set-Cookie").stream().filter(c->c.startsWith("XSRF-TOKEN=")).map(c->c.split(";")[0]).findFirst().orElseThrow();
        return post(path,"application/x-www-form-urlencoded",body+"&_csrf="+csrf.group(1),"Cookie",cookie+"; "+token);
    }
    @ParameterizedTest @EnumSource(PickupAction.class)
    void staffSeesSnapshotsHistoryAndOnlyNextFixedActionWithCsrf(PickupAction action) throws Exception {
        var row=row(action.getExpectedStatus(),action,null);when(pickup.getPickupOrder(STAFF,10L)).thenReturn(detail(row));
        var page=get(ROOT+"/10","Cookie",cookie(RoleName.STAFF));assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("data-layout=\"staff\"",ROOT+"/10/"+action.getPath(),"name=\"_csrf\"","175.000 ₫","Nhân viên A","Snapshot &lt;script&gt;")
                .doesNotContain("<script>alert(1)</script>","name=\"amount\"","name=\"storeId\"","name=\"targetStatus\"");
        for(var other:PickupAction.values())if(other!=action)assertThat(page.body()).doesNotContain(ROOT+"/10/"+other.getPath());
        if(action==PickupAction.COMPLETE)assertThat(page.body()).contains("name=\"pickupCode\"","đã thu đủ");
        else assertThat(page.body()).doesNotContain("name=\"pickupCode\"");
    }
    @Test void staffListHasWorkingSidebarAndNoCode() throws Exception {
        when(pickup.getPickupOrders(STAFF)).thenReturn(List.of(row(OrderStatus.CONFIRMED,PickupAction.PREPARE,null)));
        var page=get(ROOT+"?storeId=999","Cookie",cookie(RoleName.STAFF));assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("data-order-id=\"10\"","href=\""+ROOT+"\"","Người nhận");verify(pickup).getPickupOrders(STAFF);
    }
    @ParameterizedTest @EnumSource(value=OrderStatus.class,names={"PENDING_PAYMENT","CONFIRMED","COMPLETED","CANCELLED"})
    void blockedOrTerminalOrderHasNoStaffAction(OrderStatus state) throws Exception {
        when(pickup.getPickupOrder(STAFF,10L)).thenReturn(detail(row(state,null,null)));
        var page=get(ROOT+"/10","Cookie",cookie(RoleName.STAFF));assertThat(page.statusCode()).isEqualTo(200);
        for(var action:PickupAction.values())assertThat(page.body()).doesNotContain(ROOT+"/10/"+action.getPath());
    }
    @Test void fixedPostsBindOnlyPrincipalOrderAndPickupCode() throws Exception {
        String staff=cookie(RoleName.STAFF);String forged="storeId=999&userId=1&staffEmail=admin&amount=1&targetStatus=CANCELLED&paymentStatus=PAID&pickedUpAt=2000&transactionCode=HACK";
        for(var action:PickupAction.values())assertThat(postAs(staff,ROOT+"/10/"+action.getPath(),forged+"&pickupCode=ABCD2345").statusCode()).isEqualTo(302);
        verify(pickup).startPreparingPickup(STAFF,10L);verify(pickup).markReadyForPickup(STAFF,10L);verify(pickup).completePickup(STAFF,10L,"ABCD2345");
    }
    @Test void allMutationsRequireCsrfForCookiesBearerAndEncodedPaths() throws Exception {
        String staff=cookie(RoleName.STAFF),bearer="Bearer "+staff.substring("ONESHOP_TOKEN=".length());
        for(var action:PickupAction.values()) {
            String path=ROOT+"/10/"+action.getPath();assertThat(post(path,"application/x-www-form-urlencoded","","Cookie",staff).statusCode()).isEqualTo(403);
            assertThat(post(path,"application/x-www-form-urlencoded","_csrf=WRONG","Cookie",staff+"; XSRF-TOKEN=REAL").statusCode()).isEqualTo(403);
            for(String root:List.of(ROOT,"/%73taff/orders/pickup","/staff/%6frders/pickup","/staff/orders/%70ickup"))
                assertThat(post(root+"/10/"+action.getPath(),"application/x-www-form-urlencoded","pickupCode=ABCD2345","Authorization",bearer).statusCode()).isEqualTo(403);
        }verifyNoInteractions(pickup);
    }
    @Test void roleAndAssignmentBoundariesBlockCustomerAdminGuestAndUnassignedStaff() throws Exception {
        for(var role:List.of(RoleName.ADMIN,RoleName.CUSTOMER)) {
            String user=cookie(role);assertThat(get(ROOT,"Cookie",user).statusCode()).isEqualTo(403);
            assertThat(get(ROOT+"/10","Cookie",user).statusCode()).isEqualTo(403);
            for(var action:PickupAction.values())assertThat(postAs(user,ROOT+"/10/"+action.getPath(),"").statusCode()).isEqualTo(403);
        }
        assertThat(get(ROOT).statusCode()).isEqualTo(302);assertThat(postAs("",ROOT+"/10/prepare","").statusCode()).isEqualTo(302);
        String staff=cookie(RoleName.STAFF);when(storeService.getAssignedStores(STAFF)).thenReturn(List.of());
        assertThat(get(ROOT,"Cookie",staff).statusCode()).isEqualTo(403);assertThat(postAs(staff,ROOT+"/10/complete","pickupCode=ABCD2345").statusCode()).isEqualTo(403);verifyNoInteractions(pickup);
    }
    @Test void getCannotMutateAndGenericTransitionDoesNotExist() throws Exception {
        String staff=cookie(RoleName.STAFF);for(var action:PickupAction.values())assertThat(get(ROOT+"/10/"+action.getPath(),"Cookie",staff).statusCode()).isEqualTo(405);
        assertThat(postAs(staff,ROOT+"/10/transition","targetStatus=COMPLETED").statusCode()).isEqualTo(404);verifyNoInteractions(pickup);
    }
    @Test void customerDetailShowsCodeAndTimelineAndOffersNoCompletion() throws Exception {
        when(orders.getOrder(STAFF,10L)).thenReturn(detail(row(OrderStatus.READY_FOR_PICKUP,null,"ABCD2345")));
        when(orders.getOrders(STAFF)).thenReturn(List.of(row(OrderStatus.READY_FOR_PICKUP,null,null)));
        String customer=cookie(RoleName.CUSTOMER);var page=get("/orders/10?userId=999","Cookie",customer);
        assertThat(page.statusCode()).isEqualTo(200);assertThat(page.body()).contains("id=\"pickup-code\"","ABCD2345","Nhân viên A","1 Đường thử","Snapshot &lt;script&gt;").doesNotContain("name=\"pickupCode\"","/complete");
        assertThat(get("/orders","Cookie",customer).body()).contains("/orders/10").doesNotContain("ABCD2345");verify(orders).getOrder(STAFF,10L);
        assertThat(postAs(customer,"/orders/10/complete","pickupCode=ABCD2345").statusCode()).isEqualTo(404);
    }
    @Test void customerOwnership404AndRoleBoundariesPreventLeaks() throws Exception {
        when(orders.getOrder(STAFF,10L)).thenThrow(new ResourceNotFoundException("Không tìm thấy đơn hàng của bạn."));
        var page=get("/orders/10?userId=1","Cookie",cookie(RoleName.CUSTOMER));assertThat(page.statusCode()).isEqualTo(404);assertThat(page.body()).doesNotContain("pickup-code","ABCD2345");
        for(var role:List.of(RoleName.STAFF,RoleName.ADMIN))assertThat(get("/orders/10","Cookie",cookie(role)).statusCode()).isEqualTo(403);
        assertThat(get("/orders/10").statusCode()).isEqualTo(302);
    }
    @Test void safeErrorsHideDatabaseDetailsAndPickupCode() throws Exception {
        String staff=cookie(RoleName.STAFF);
        for(var exception:List.of(new CannotAcquireLockException("SQL secret ABCD2345"),new DataIntegrityViolationException("SQL CHECK ABCD2345"))) {
            doThrow(exception).when(pickup).markReadyForPickup(STAFF,10L);
            var page=postAs(staff,ROOT+"/10/ready","");assertThat(page.statusCode()).isEqualTo(409);assertThat(page.body()).doesNotContain("SQL","ABCD2345","Exception");
        }
        doThrow(new AccessDeniedException("secret ABCD2345")).when(pickup).startPreparingPickup(STAFF,10L);
        assertThat(postAs(staff,ROOT+"/10/prepare","").statusCode()).isEqualTo(403);
        doThrow(new BadRequestException("Mã nhận hàng không hợp lệ.")).when(pickup).completePickup(STAFF,10L,"WRONG999");
        var page=postAs(staff,ROOT+"/10/complete","pickupCode=WRONG999");assertThat(page.statusCode()).isEqualTo(400);assertThat(page.body()).doesNotContain("WRONG999","ABCD2345");
    }
}

package com.oneshop;

import com.oneshop.dto.request.*;
import com.oneshop.dto.response.*;
import com.oneshop.entity.*;
import com.oneshop.exception.*;
import com.oneshop.repository.*;
import com.oneshop.security.jwt.JwtService;
import com.oneshop.security.service.CustomUserDetailsService;
import com.oneshop.service.*;
import com.oneshop.service.fulfillment.*;
import com.oneshop.service.payment.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** BE-07 production-stack fixtures. No V2 checkout/Staff/payment adapter; all writes use current V3 services/URLs. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "app.jwt.secret=test-only-secret-test-only-secret-1234567890", "oneshop.checkout.online-enabled=true",
        "oneshop.payment.timeout-worker-enabled=false"})
public abstract class V3RegressionTestSupport extends ReviewV3TestSupport {
    @LocalServerPort int port;
    @Autowired CustomerOrderService customers;
    @Autowired PaymentService paymentService;
    @Autowired InventoryService inventory;
    @Autowired StaffInventoryService staffInventory;
    @Autowired StaffOperationsService operations;
    @Autowired StoreProductService stockService;
    @Autowired OrderService legacyState;
    @Autowired PaymentTimeoutService timeouts;
    @Autowired AdminPaymentService adminPayments;
    @Autowired JwtService jwt;
    @Autowired CustomUserDetailsService userDetails;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    @MockitoSpyBean PaymentRepository paymentRows;
    @MockitoSpyBean OrderFulfillmentStatusHistoryRepository partHistory;
    @MockitoSpyBean OrderStatusHistoryRepository orderHistory;
    @MockitoSpyBean InventoryMovementRepository movementRows;
    private List<Map<String,Object>> productMetadata;
    private final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach void rememberProductMetadata() { productMetadata=jdbc.queryForList("select product_id,name,price,original_price,updated_at from dbo.products"); }
    @AfterEach void restoreProductMetadataAndFaults() {
        reset(paymentRows,partHistory,orderHistory,movementRows);
        if(productMetadata!=null) for(var r:productMetadata) jdbc.update("update dbo.products set name=?,price=?,original_price=?,updated_at=? where product_id=?",
                r.get("name"),r.get("price"),r.get("original_price"),r.get("updated_at"),r.get("product_id"));
    }
    long single(FulfillmentType type, PaymentMethod method, boolean pay) {
        long id;
        if(type==FulfillmentType.STORE_PICKUP) id=method==PaymentMethod.ONLINE
                ?data.onlinePickup("OS-THUDUC",SERUM,1):data.payAtStorePickup("OS-THUDUC",SERUM,1);
        else if(method==PaymentMethod.ONLINE) {
            data.isolate(SERUM,"OS-THUDUC",21); long line=line(ALICE,SERUM,1,true);
            id=checkout.placeOrder(ALICE,request(List.of(line),type,method,null,false,null,null)).orders().getFirst().orderId();
        } else id=data.codDelivery("OS-THUDUC",SERUM,1);
        if(pay && method==PaymentMethod.ONLINE) settle(id,true);
        return id;
    }
    long cod(){return single(FulfillmentType.DELIVERY,PaymentMethod.COD,false);}
    protected long selected(int stock,int quantity){data.isolate(SERUM,"OS-THUDUC",stock);return line(ALICE,SERUM,quantity,true);}
    protected CheckoutResultResponse place(List<Long> ids){return checkout.placeOrder(ALICE,request(ids,FulfillmentType.DELIVERY,PaymentMethod.COD,null,false,null,null));}
    CheckoutResultResponse splitCod(){
        data.isolate(SERUM,"OS-THUDUC",2);data.stockAt(SERUM,"OS-GOVAP",2);long l=line(ALICE,SERUM,4,true);
        var p=checkout.preview(ALICE,List.of(l),FulfillmentType.DELIVERY,PaymentMethod.COD,null,ADDRESS);
        return checkout.placeOrder(ALICE,request(List.of(l),FulfillmentType.DELIVERY,PaymentMethod.COD,null,true,p.plan().signature(),p.grandTotal()));
    }
    Map<String,List<Map<String,Object>>> checkoutState(){
        Map<String,List<Map<String,Object>>> s=new LinkedHashMap<>();
        for(String t:List.of("checkout_sessions","orders","order_items","order_fulfillments","order_status_history","order_fulfillment_status_history","payments","cart_items","store_products","inventory_movements"))
            s.put(t,jdbc.queryForList("select * from dbo."+t+" order by 1"));return s;
    }
    String checkoutForm(long line,FulfillmentType type,PaymentMethod method,Long store){return "cartItemIds="+line+"&fulfillmentType="+type+"&paymentMethod="+method+"&receiverName=Customer&receiverPhone=0912345678&shippingAddress="+enc(ADDRESS)+(store==null?"":"&pickupStoreId="+store);}
    long online(){return single(FulfillmentType.DELIVERY,PaymentMethod.ONLINE,false);}
    long pickup(PaymentMethod method,boolean pay){return single(FulfillmentType.STORE_PICKUP,method,pay);}
    long part(long id){return data.onlyFulfillment(id);}
    protected long line(String email,String sku,int quantity,boolean clear){
        if(clear)cartRepository.findByUserEmail(email).ifPresent(c->cartItems.deleteAll(cartItems.findByCartId(c.getId())));
        carts.addItem(email,AddCartItemRequest.forProduct(data.productId(sku),quantity));
        return cartItems.findByCartIdOrderByIdAsc(cartRepository.findByUserEmail(email).orElseThrow().getId()).stream()
                .filter(i->i.getProduct().getSku().equals(sku)).findFirst().orElseThrow().getId();
    }
    PlaceOrderRequest request(List<Long> lines,FulfillmentType type,PaymentMethod method,Long pickup,boolean consent,String signature,BigDecimal total){
        return new PlaceOrderRequest(lines,type,method,"Người Nhận","0912345678",type==FulfillmentType.DELIVERY?ADDRESS:null,pickup,consent,signature,total);
    }
    void settle(long id,boolean success){customer(ALICE,()->payment.simulate(ALICE,id,data.attemptId(id),success?
            PaymentReconciliationService.SimulatedResult.SUCCESS:PaymentReconciliationService.SimulatedResult.FAILED));}
    void cancel(long id){customer(ALICE,()->{customers.cancelOrder(ALICE,id);return null;});}
    void act(long id,FulfillmentAction action){act(id,action,null);}
    void act(long id,FulfillmentAction action,String input){data.asStaff(STAFF_THUDUC,()->staff.perform(part(id),action,input));}
    FulfillmentAction deliveryAction(DeliveryAction a){return switch(a){case PREPARE->FulfillmentAction.PREPARE;case PACK->FulfillmentAction.PACK;case SHIP->FulfillmentAction.SHIP;case COMPLETE->FulfillmentAction.COMPLETE_DELIVERY;};}
    FulfillmentAction pickupAction(PickupAction a){return switch(a){case PREPARE->FulfillmentAction.PREPARE;case READY->FulfillmentAction.MARK_READY;case COMPLETE->FulfillmentAction.COMPLETE_PICKUP;};}
    void toStage(long id,OrderFulfillmentStatus stage){
        if(stage==OrderFulfillmentStatus.CONFIRMED)return;
        act(id,FulfillmentAction.PREPARE);
        if(stage==OrderFulfillmentStatus.PREPARING)return;
        boolean pickup=jdbc.queryForObject("select fulfillment_type from dbo.orders where order_id=?",String.class,id).equals("STORE_PICKUP");
        if(pickup){act(id,FulfillmentAction.MARK_READY);if(stage==OrderFulfillmentStatus.COMPLETED)act(id,FulfillmentAction.COMPLETE_PICKUP,data.pickupCode(part(id)));}
        else {act(id,FulfillmentAction.PACK);if(stage==OrderFulfillmentStatus.PACKED)return;act(id,FulfillmentAction.SHIP);
            if(stage==OrderFulfillmentStatus.COMPLETED)act(id,FulfillmentAction.COMPLETE_DELIVERY);}
    }
    Map<String,List<Map<String,Object>>> state(long id){
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
        for(String table:List.of("orders","order_items","payments","order_status_history"))result.put(table,jdbc.queryForList("select * from dbo."+table+" where order_id=? order by 1",id));
        result.put("parts",jdbc.queryForList("select * from dbo.order_fulfillments where order_id=? order by 1",id));
        result.put("partHistory",jdbc.queryForList("select h.* from dbo.order_fulfillment_status_history h join dbo.order_fulfillments f on f.fulfillment_id=h.fulfillment_id where f.order_id=? order by 1",id));
        result.put("stock",jdbc.queryForList("select * from dbo.store_products order by 1"));
        result.put("movements",jdbc.queryForList("select * from dbo.inventory_movements order by 1"));return result;
    }
    void unchanged(long id,Runnable action,Class<? extends Throwable> error){var before=state(id);assertThatThrownBy(action::run).isInstanceOf(error);assertThat(state(id)).isEqualTo(before);}
    void lifecycle(FulfillmentType type,PaymentMethod method,boolean overHttp,boolean rename) throws Exception{
        long id=single(type,method,true);var before=state(id);String oldName=before.get("order_items").getFirst().get("product_name").toString();
        if(rename)jdbc.update("update dbo.products set name=N'Đổi tên sau mua',price=price+12345,original_price=null where product_id=?",data.productId(SERUM));
        List<FulfillmentAction> actions=type==FulfillmentType.DELIVERY?List.of(FulfillmentAction.PREPARE,FulfillmentAction.PACK,FulfillmentAction.SHIP,FulfillmentAction.COMPLETE_DELIVERY)
                :List.of(FulfillmentAction.PREPARE,FulfillmentAction.MARK_READY,FulfillmentAction.COMPLETE_PICKUP);
        for(var a:actions){String input=a==FulfillmentAction.COMPLETE_PICKUP?data.pickupCode(part(id)):null;
            if(overHttp)assertThat(postAs(STAFF_THUDUC,"/staff/fulfillments/"+part(id)+"/"+a.getPath(),"pickupCode="+enc(input)+"&orderStatus=CANCELLED&paymentStatus=PAID&storeId=2",true).statusCode()).isEqualTo(302);
            else act(id,a,input);
            assertThat(data.fulfillmentStatus(part(id))).isEqualTo(a.getTo().name());
            assertThat(state(id).get("stock")).isEqualTo(before.get("stock"));assertThat(state(id).get("movements")).isEqualTo(before.get("movements"));
            assertThat(state(id).get("order_items")).isEqualTo(before.get("order_items"));
            assertThat(data.successfulPayments(id)).isEqualTo(method==PaymentMethod.ONLINE||a.isCompletion()?1:0);
        }
        assertThat(data.orderStatus(id)).isEqualTo("COMPLETED");assertThat(data.paymentStatus(id)).isEqualTo("PAID");
        var detail=data.asStaff(STAFF_THUDUC,()->staff.getFulfillment(part(id)));
        assertThat(detail.items()).extracting(OrderItemResponse::productName).contains(oldName);
        assertThat(detail.statusHistory()).hasSize(actions.size()+1);
        assertThat(count("select count(*) from dbo.order_fulfillment_status_history where fulfillment_id=? and changed_by_user_id=?",part(id),userId(STAFF_THUDUC))).isEqualTo(actions.size());
    }
    void inject(String point){
        org.mockito.stubbing.Answer<Object> fault=i->{entityManager.persist(i.getArgument(0)); entityManager.flush(); throw new IllegalStateException("BE07 injected "+point);};
        if(point.equals("CLOSE_PENDING_FLUSH"))doAnswer(i->{entityManager.flush();throw new IllegalStateException("BE07 closed pending attempt flushed");}).when(paymentRows).flush();
        else if(point.contains("RESTORE")||point.contains("ITEM"))doAnswer(fault).when(movementRows).save(any());
        else if(point.contains("PAYMENT")||point.contains("PAID")){doAnswer(fault).when(paymentRows).save(any());doAnswer(fault).when(paymentRows).saveAndFlush(any());}
        else if(point.contains("ORDER")||point.contains("TRANSITION"))doAnswer(fault).when(orderHistory).save(any());
        else doAnswer(fault).when(partHistory).save(any());
    }
    void rollbackStep(FulfillmentType type,PaymentMethod method,boolean ready,String point){
        long id=single(type,method,true);toStage(id,ready?OrderFulfillmentStatus.PREPARING:type==FulfillmentType.DELIVERY?OrderFulfillmentStatus.SHIPPING:OrderFulfillmentStatus.READY_FOR_PICKUP);
        var before=state(id);FulfillmentAction action=ready?FulfillmentAction.MARK_READY:type==FulfillmentType.DELIVERY?FulfillmentAction.COMPLETE_DELIVERY:FulfillmentAction.COMPLETE_PICKUP;
        // Every named fault point is backed by the matching current persistence boundary, not a test-only workflow.
        if(ready&&(point.contains("PAYMENT")||point.contains("PAID")))point="HISTORY_SAVE";
        if(ready&&(point.contains("ORDER")||point.contains("TRANSITION")))point="HISTORY_SAVE";
        if(method==PaymentMethod.ONLINE&&(point.contains("PAYMENT")||point.contains("PAID")))point="HISTORY_SAVE";
        inject(point);
        assertThatThrownBy(()->tx.execute(status->{act(id,action,action==FulfillmentAction.COMPLETE_PICKUP?data.pickupCode(part(id)):null);return null;})).isInstanceOf(IllegalStateException.class);
        reset(paymentRows,partHistory,orderHistory,movementRows);assertThat(state(id)).isEqualTo(before);
    }
    List<Object> race(Callable<?>... tasks)throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(tasks.length);CountDownLatch go=new CountDownLatch(1);
        try{List<Future<Object>> futures=new ArrayList<>();for(var task:tasks)futures.add(pool.submit(()->{go.await();try{task.call();return "ok";}catch(Exception e){return e;}}));
            go.countDown();List<Object> results=new ArrayList<>();for(var f:futures)results.add(f.get(40,TimeUnit.SECONDS));return results;
        }finally{pool.shutdownNow();}
    }
    void raceStock(int stock,int quantity)throws Exception{
        long a=selected(stock,quantity),b=line(BOB,SERUM,quantity,true);long before=count("select count(*) from dbo.orders");
        var outcomes=race(()->place(List.of(a)),()->checkout.placeOrder(BOB,request(List.of(b),FulfillmentType.DELIVERY,PaymentMethod.COD,null,false,null,null)));
        assertThat(outcomes.stream().filter("ok"::equals).count()).isEqualTo(1);assertThat(count("select count(*) from dbo.orders")).isEqualTo(before+1);
        assertThat(jdbc.queryForObject("select quantity from dbo.store_products where store_product_id=?",Integer.class,data.storeProduct(SERUM,"OS-THUDUC"))).isEqualTo(stock-quantity);
        assertThat(count("select count(*) from dbo.store_products where quantity<0 or reserved_quantity<0 or reserved_quantity>quantity")).isZero();
    }
    void holdWhileIndependent(Runnable first,Runnable second)throws Exception{
        var pool=Executors.newSingleThreadExecutor();var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        var future=pool.submit(()->tx.execute(s->{first.run();entityManager.flush();held.countDown();try{if(!release.await(25,TimeUnit.SECONDS))throw new IllegalStateException("release timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return null;}));
        try{assertThat(held.await(15,TimeUnit.SECONDS)).isTrue();second.run();assertThat(future.isDone()).isFalse();}
        finally{release.countDown();try{future.get(35,TimeUnit.SECONDS);}finally{pool.shutdownNow();}}
    }
    void transitionRace(FulfillmentType type,PaymentMethod method,FulfillmentAction action)throws Exception{
        long id=single(type,method,true);toStage(id,action.getFrom());int history=data.fulfillmentHistory(part(id));var before=state(id);
        String code=action==FulfillmentAction.COMPLETE_PICKUP?data.pickupCode(part(id)):null;
        var outcomes=race(()->{act(id,action,code);return null;},()->{act(id,action,code);return null;});
        assertThat(outcomes.stream().filter("ok"::equals).count()).isEqualTo(1);assertThat(data.fulfillmentHistory(part(id))).isEqualTo(history+1);
        assertThat(state(id).get("stock")).isEqualTo(before.get("stock"));assertThat(state(id).get("movements")).isEqualTo(before.get("movements"));
        assertThat(data.successfulPayments(id)).isEqualTo(method==PaymentMethod.ONLINE||action.isCompletion()?1:0);
    }
    void revoke(String what){
        switch(what){case "STORE"->jdbc.update("update dbo.stores set status='INACTIVE' where store_id=1");
            case "USER"->jdbc.update("update dbo.users set status='INACTIVE' where email=?",STAFF_THUDUC);
            default->jdbc.update("update dbo.staff_store_assignments set status='INACTIVE' where user_id=?",userId(STAFF_THUDUC));}
    }
    HttpResponse<String> send(String method,String path,String body,String... headers)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Accept","text/html");
        if(headers.length>0)builder.headers(headers);if(method.equals("GET"))builder.GET();else builder.header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    String cookie(String email){return "ONESHOP_TOKEN="+jwt.generateToken(userDetails.loadUserByUsername(email));}
    HttpResponse<String> getAs(String email,String path)throws Exception{return email==null?send("GET",path,""):send("GET",path,"","Cookie",cookie(email));}
    HttpResponse<String> postAs(String email,String path,String body,boolean csrf)throws Exception{
        String c=email==null?"":cookie(email);if(!csrf)return send("POST",path,body,"Cookie",c);
        var page=send("GET",email==null?"/login":"/","","Cookie",c);var token=Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());assertThat(token.find()).isTrue();
        String xsrf=page.headers().allValues("Set-Cookie").stream().filter(x->x.startsWith("XSRF-TOKEN=")).map(x->x.split(";")[0]).findFirst().orElseThrow();
        return send("POST",path,body+"&_csrf="+token.group(1),"Cookie",c+"; "+xsrf);
    }
    String enc(String text){return URLEncoder.encode(text==null?"":text,StandardCharsets.UTF_8);}
    void assertNoLegacyMutation(long id)throws Exception{
        var before=state(id);for(String path:List.of("/staff/orders/"+id+"/delivery/prepare","/staff/orders/delivery/"+id+"/prepare","/staff/orders/"+id+"/pickup/ready"))
            assertThat(postAs(STAFF_THUDUC,path,"orderStatus=COMPLETED",true).statusCode()).isIn(404,405);
        assertThatThrownBy(()->checkout.placeOrder(ALICE,new CheckoutRequest(List.of(),List.of()))).isInstanceOf(BadRequestException.class);
        assertThat(state(id)).isEqualTo(before);
    }
}

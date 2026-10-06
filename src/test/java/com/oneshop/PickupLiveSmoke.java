package com.oneshop;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.Reader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Standalone HTTP/JDBC check against the packaged dev JAR; run on seed DB without other writers. */
public final class PickupLiveSmoke {
    private static final String CUSTOMER = "khachhang1@example.com";
    private static final String STAFF = "staff.thuduc@oneshop.vn";
    private static final String ROOT = "/staff/orders/pickup";
    private static final List<String> ACTIONS = List.of("prepare", "ready", "complete");
    private static final List<String> FROM = List.of("CONFIRMED", "PREPARING", "READY_FOR_PICKUP");
    private static final List<String> TO = List.of("PREPARING", "READY_FOR_PICKUP", "COMPLETED");
    private static final List<String> TABLES = List.of("orders", "order_items", "payments", "order_status_history",
            "inventory_movements", "store_products", "checkout_sessions", "cart_items", "carts");
    private final JdbcTemplate jdbc;
    private final String base;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private PickupLiveSmoke(JdbcTemplate jdbc, String base) { this.jdbc = jdbc; this.base = base; }

    public static void main(String[] args) throws Exception {
        Properties config = new Properties();
        if (Files.exists(Path.of(".env"))) {
            try (Reader reader = Files.newBufferedReader(Path.of(".env"), StandardCharsets.UTF_8)) { config.load(reader); }
        }
        String url = "jdbc:sqlserver://" + value(config, "DB_HOST", "localhost") + ":" + value(config, "DB_PORT", "1433")
                + ";databaseName=" + value(config, "DB_NAME", "oneshop") + ";encrypt=true;trustServerCertificate="
                + value(config, "DB_TRUST_SERVER_CERTIFICATE", "false");
        DriverManagerDataSource source = new DriverManagerDataSource(url, value(config, "DB_USERNAME", ""), value(config, "DB_PASSWORD", ""));
        source.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        new PickupLiveSmoke(new JdbcTemplate(source), args.length == 0 ? "http://localhost:18081" : args[0]).run();
    }

    private static String value(Properties config, String name, String fallback) {
        return System.getenv().getOrDefault(name, config.getProperty(name, fallback));
    }

    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (String table : TABLES) result.put(table, jdbc.queryForList("select * from dbo." + table + " order by 1"));
        return result;
    }

    private static Map<String, Integer> counts(Map<String, List<Map<String, Object>>> rows) {
        Map<String, Integer> result = new LinkedHashMap<>();
        rows.forEach((table, data) -> result.put(table, data.size())); return result;
    }

    private void run() throws Exception {
        var before=snapshot(); SeedGuard guard=new SeedGuard(jdbc);guard.remember();
        try {
            check(get("/health","").statusCode()==200,"dev JAR health");
            String customer=login(CUSTOMER),staff=login(STAFF),otherStaff=login("staff.govap@oneshop.vn"),otherCustomer=login("khachhang2@example.com");
            long mixed=place(customer,List.of(add(customer,"COCOON-SERUM-30",1),add(customer,"INNI-TONER-200",2),add(customer,"INNI-CLEANS-120",4)),
                    List.of(group(0,1,"STORE_PICKUP","PAY_AT_STORE"),group(1,2,"DELIVERY","COD"),group(2,4,"STORE_PICKUP","ONLINE")));
            var orders=jdbc.queryForList("select * from dbo.orders where checkout_id=? order by 1",mixed);
            long id=((Number)orders.stream().filter(o->((Number)o.get("store_id")).intValue()==1).findFirst().orElseThrow().get("order_id")).longValue();
            var neighbors=orders.stream().filter(o->((Number)o.get("order_id")).longValue()!=id).toList();
            check(get(ROOT,staff).body().contains("data-order-id=\""+id+"\""),"pickup scoped list");
            flow(customer,staff,otherCustomer,id,true);
            for(var neighbor:neighbors)check(jdbc.queryForMap("select * from dbo.orders where order_id=?",neighbor.get("order_id")).equals(neighbor),"neighbor order exact unchanged");
            System.out.println("Live TC-10 / Customer code / wrong code / one PAY_AT_STORE receipt / Staff histories / exact stock+movement+snapshot / isolation: PASS");

            long online=create(customer,"STORE_PICKUP","ONLINE");
            check(post(customer,"/orders/"+online+"/payments/attempts","").statusCode()==302,"online attempt");
            long payment=jdbc.queryForObject("select payment_id from dbo.payments where order_id=?",Long.class,online);
            String success="/orders/"+online+"/payments/"+payment+"/success";
            check(post(customer,success,"").statusCode()==302,"online success");var receipt=receipts(online);
            flow(customer,staff,otherCustomer,online,false);check(receipts(online).equals(receipt),"online receipt unchanged");
            check(post(customer,success,"").statusCode()==302 && receipts(online).equals(receipt),"online result stays idempotent after pickup completed");
            System.out.println("Live ONLINE pickup / unchanged receipt / picked_up_at / terminal / duplicate: PASS");

            long pending=create(customer,"STORE_PICKUP","ONLINE"),delivery=create(customer,"DELIVERY","COD"),ready=create(customer,"STORE_PICKUP","PAY_AT_STORE");
            check(post(staff,ROOT+"/"+ready+"/prepare","").statusCode()==302,"guard fixture prepare");check(post(staff,ROOT+"/"+ready+"/ready","").statusCode()==302,"guard fixture ready");
            String code=code(ready);var original=snapshot();
            check(get(ROOT+"/"+ready,otherStaff).statusCode()==404,"cross Store read 404");
            check(get("/orders/"+ready+"?userId=1",otherCustomer).statusCode()==404,"other Customer cannot read code");
            check(!get(ROOT+"/"+ready,staff).body().contains(code),"stored code absent from staff UI");
            String bearer="Bearer "+staff.substring("ONESHOP_TOKEN=".length());
            for(String action:ACTIONS) {
                String suffix="/"+ready+"/"+action;
                check(post(otherStaff,ROOT+suffix,"pickupCode="+code+"&storeId=2").statusCode()==403,"cross Store knows code but cannot act");
                check(post(staff,ROOT+"/"+pending+"/"+action,"").statusCode()==400,"pending guard");
                check(post(staff,ROOT+"/"+delivery+"/"+action,"").statusCode()==400,"DELIVERY isolation");
                check(raw(ROOT+suffix,"pickupCode="+code,"Cookie",staff).statusCode()==403,"missing CSRF");
                check(raw(ROOT+suffix,"_csrf=WRONG","Cookie",staff+"; XSRF-TOKEN=REAL").statusCode()==403,"wrong CSRF");
                for(String root:List.of(ROOT,"/%73taff/orders/pickup","/staff/%6frders/pickup","/staff/orders/%70ickup"))
                    check(raw(root+suffix,"pickupCode="+code,"Authorization",bearer).statusCode()==403,"Bearer and encoded path CSRF");
                check(get(ROOT+suffix,staff).statusCode()==405,"GET actions cannot mutate");
            }
            String admin=login("admin@oneshop.vn");
            for(String account:List.of(customer,admin)) {
                check(get(ROOT,account).statusCode()==403,"role staff-only");
                check(post(account,ROOT+"/"+ready+"/complete","pickupCode="+code).statusCode()==403,"role mutation");
            }
            check(get(ROOT,"").statusCode()==302,"guest denied");
            check(post("",ROOT+"/"+ready+"/complete","pickupCode="+code).statusCode()==302,"guest cannot complete");
            check(post(customer,"/orders/"+ready+"/complete","pickupCode="+code).statusCode()==404,"Customer read only");
            check(snapshot().equals(original),"all guards leave exact rows unchanged");
            System.out.println("Live pending / cross Store / ownership / DELIVERY isolation / role / CSRF cookie+Bearer+encoded / GET: PASS");
        }finally {
            guard.restore();var after=snapshot();check(after.equals(before),"exact fixture cleanup");
            System.out.println("Cleanup exact rows: PASS; before="+counts(before)+"; after="+counts(after));
        }
    }
    private String code(long id) {return jdbc.queryForObject("select pickup_code from dbo.orders where order_id=?",String.class,id);}
    private void flow(String customer,String staff,String otherCustomer,long id,boolean offline) throws Exception {
        var stock=jdbc.queryForList("select * from dbo.store_products order by 1");
        var movements=jdbc.queryForList("select * from dbo.inventory_movements order by 1");
        var items=jdbc.queryForList("select * from dbo.order_items where order_id=?",id);var receiptsBefore=receipts(id);
        int before=jdbc.queryForObject("select count(*) from dbo.order_status_history where order_id=?",Integer.class,id);
        String code=null;Object readyTime=null;
        check(!get("/orders/"+id,customer).body().contains("id=\"pickup-code\""),"code hidden before ready");
        for(int i=0;i<ACTIONS.size();i++) {
            String action=ACTIONS.get(i),path=ROOT+"/"+id+"/"+action;
            var page=get(ROOT+"/"+id,staff);check(page.statusCode()==200 && page.body().contains(path),"next fixed action");
            if(i==2) {
                code=code(id);readyTime=jdbc.queryForObject("select ready_at from dbo.orders where order_id=?",java.sql.Timestamp.class,id);
                var client=get("/orders/"+id,customer);check(client.statusCode()==200 && client.body().contains("id=\"pickup-code\"") && client.body().contains(code),"owner sees READY code");
                check(get("/orders/"+id,otherCustomer).statusCode()==404,"other Customer code privacy");
                check(!page.body().contains(code),"Staff input doesn't expose stored code");var original=snapshot();
                var wrong=post(staff,path,"pickupCode=WRONG999");check(wrong.statusCode()==400 && !wrong.body().contains(code),"wrong code safely rejected");check(snapshot().equals(original),"wrong code zero mutation");
            }
            String body="amount=1&paymentStatus=PAID&targetStatus=CANCELLED&readyAt=2000&pickedUpAt=2000&transactionCode=HACK&storeId=2"+(i==2?"&pickupCode="+code:"");
            check(post(staff,path,body).statusCode()==302,"pickup action");
            var order=jdbc.queryForMap("select * from dbo.orders where order_id=?",id);
            check(order.get("order_status").equals(TO.get(i)),"fixed state");check(order.get("payment_status").equals(!offline || i==2 ? "PAID":"UNPAID"),"payment gate");
            check(order.get("picked_up_at")!=null == (i==2),"picked_up_at completion only");
            if(i==1) {check(code(id).matches("[A-HJ-NP-Z2-9]{8}") && order.get("ready_at")!=null,"backend code+ready time");}
            check(receipts(id).size()==(offline && i<2?0:1),"no early Payment");if(!offline)check(receipts(id).equals(receiptsBefore),"online receipt unchanged each action");
            var original=snapshot();check(post(staff,path,i==2?"pickupCode="+code:"").statusCode()==400,"duplicate explicit rejection");check(snapshot().equals(original),"duplicate preserves code/time/history/payment");
            check(jdbc.queryForList("select * from dbo.store_products order by 1").equals(stock),"stock exact unchanged");
            check(jdbc.queryForList("select * from dbo.inventory_movements order by 1").equals(movements),"movement exact unchanged");
            check(jdbc.queryForList("select * from dbo.order_items where order_id=?",id).equals(items),"snapshots exact unchanged");
        }
        var order=jdbc.queryForMap("select * from dbo.orders where order_id=?",id);check(order.get("pickup_code").equals(code) && order.get("ready_at").equals(readyTime),"completed code+ready time stable");
        var histories=jdbc.queryForList("select * from dbo.order_status_history where order_id=? order by history_id",id);
        long actor=jdbc.queryForObject("select user_id from dbo.users where email=?",Long.class,STAFF);
        check(histories.size()==before+3,"exact three fulfillment histories");
        for(int i=0;i<3;i++){var h=histories.get(before+i);check(h.get("old_status").equals(FROM.get(i)) && h.get("new_status").equals(TO.get(i)) && ((Number)h.get("changed_by_user_id")).longValue()==actor,"staff history edge+actor");}
        if(offline){var p=receipts(id).get(0);check(p.get("method").equals("PAY_AT_STORE") && p.get("status").equals("SUCCESS") && p.get("paid_at")!=null
                && p.get("transaction_code").toString().matches("PAY-[0-9a-f-]{36}") && p.get("amount").equals(order.get("total_amount")),"snapshot internal Payment");}
    }
    private List<Map<String, Object>> receipts(long id) { return jdbc.queryForList("select * from dbo.payments where order_id=? order by payment_id", id); }

    private long create(String customer, String type, String method) throws Exception {
        long checkout = place(customer, List.of(add(customer, "COCOON-SERUM-30", 1)), List.of(group(0, 1, type, method)));
        return jdbc.queryForObject("select order_id from dbo.orders where checkout_id=?", Long.class, checkout);
    }

    private long add(String customer, String sku, long store) throws Exception {
        long stock = jdbc.queryForObject("select sp.store_product_id from dbo.store_products sp join dbo.products p on p.product_id=sp.product_id "
                + "where p.sku=? and sp.store_id=?", Long.class, sku, store);
        check(post(customer, "/cart/items", "storeProductId=" + stock + "&quantity=1").statusCode() == 302, "cart add");
        return jdbc.queryForObject("select ci.cart_item_id from dbo.cart_items ci join dbo.carts c on c.cart_id=ci.cart_id "
                + "join dbo.users u on u.user_id=c.user_id where u.email=? and ci.store_product_id=?", Long.class, CUSTOMER, stock);
    }

    private static String group(int index, long store, String type, String method) {
        String prefix = "groups[" + index + "].";
        return prefix + "storeId=" + store + "&" + prefix + "fulfillmentType=" + type + "&" + prefix + "paymentMethod=" + method
                + "&" + prefix + "receiverName=" + enc("Người nhận smoke") + "&" + prefix + "receiverPhone=0912345678"
                + (type.equals("DELIVERY") ? "&" + prefix + "shippingAddress=1+Smoke+Street" : "");
    }

    private long place(String customer, List<Long> lines, List<String> groups) throws Exception {
        String body = String.join("&", lines.stream().map(id -> "cartItemIds=" + id).toList()) + "&" + String.join("&", groups);
        var response = post(customer, "/checkout", body);
        check(response.statusCode() == 302, "Phase 8 checkout");
        Matcher id = Pattern.compile("/checkout/(\\d+)").matcher(response.headers().firstValue("Location").orElseThrow());
        check(id.find(), "checkout redirect"); return Long.parseLong(id.group(1));
    }

    private String login(String email) throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"OneShop@123\"}")).build(), HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == 200, "seed login");
        Matcher token = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(response.body());
        check(token.find(), "login token"); return "ONESHOP_TOKEN=" + token.group(1);
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String cookie, String path, String body) throws Exception {
        var page = get("/login", "");
        Matcher csrf = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body()); check(csrf.find(), "csrf form token");
        String tokenCookie = page.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
        return raw(path, body + "&_csrf=" + enc(csrf.group(1)), "Cookie", cookie + "; " + tokenCookie);
    }

    private HttpResponse<String> raw(String path, String body, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (headers.length > 0) request.headers(headers);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}

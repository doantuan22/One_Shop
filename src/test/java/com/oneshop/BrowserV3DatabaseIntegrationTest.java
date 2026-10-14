package com.oneshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.oneshop.FulfillmentFixtures.*;
import static org.assertj.core.api.Assertions.*;

/** BE-07: Chrome renders and submits the real MVC forms with isolated Customer/Staff/Admin contexts. */
class BrowserV3DatabaseIntegrationTest extends V3RegressionTestSupport {
    @ParameterizedTest
    @ValueSource(strings={"COD","COD_SPLIT","PICKUP","ONLINE","FAILED","REFUND","REVIEW"})
    void actualBrowserBusinessFlow(String mode)throws Exception {
        selected(20,1);
        boolean split=mode.equals("COD_SPLIT")||mode.equals("REVIEW");
        if(split){data.isolate(SERUM,"OS-THUDUC",2);data.stockAt(SERUM,"OS-GOVAP",2);}
        cartRepository.findByUserEmail(ALICE).ifPresent(c->cartItems.deleteAll(cartItems.findByCartId(c.getId())));
        Path output=Path.of("target/be07-evidence/browser",mode+"-"+System.currentTimeMillis()).toAbsolutePath();Files.createDirectories(output);
        var config=new LinkedHashMap<String,Object>();config.put("port",port);config.put("output",output.toString());config.put("mode",mode);
        config.put("productId",data.productId(SERUM));config.put("quantity",split?3:1);config.put("storeId",data.storeId("OS-THUDUC"));
        config.put("chrome","C:/Program Files/Google/Chrome/Application/chrome.exe");
        Path input=output.resolve("config.json");new ObjectMapper().writeValue(input.toFile(),config);
        var process=new ProcessBuilder("node","src/test/browser/be07-e2e.mjs",input.toString()).redirectErrorStream(true).redirectOutput(output.resolve("node.log").toFile()).start();
        try{assertThat(process.waitFor(150,TimeUnit.SECONDS)).as("Browser finished; evidence: "+output).isTrue();assertThat(process.exitValue()).as(Files.readString(output.resolve("node.log"))).isZero();}finally{if(process.isAlive())process.destroyForcibly();}
        var result=new ObjectMapper().readTree(output.resolve("result.json").toFile());long id=result.get("orderIds").get(0).asLong();
        assertThat(result.get("trace").size()).isGreaterThanOrEqualTo(3);assertThat(result.get("browser").asText()).contains("Chrome");
        switch(mode){
            case "COD"->{assertThat(data.orderStatus(id)).isEqualTo("CONFIRMED");assertThat(data.successfulPayments(id)).isZero();}
            case "COD_SPLIT"->{assertThat(result.get("orderIds").size()).isEqualTo(2);assertThat(data.orderStatus(id)).isEqualTo("CONFIRMED");}
            case "PICKUP","ONLINE"->{assertThat(data.orderStatus(id)).isEqualTo("COMPLETED");assertThat(data.successfulPayments(id)).isEqualTo(1);}
            case "FAILED"->{assertThat(data.orderStatus(id)).isEqualTo("CANCELLED");assertThat(data.cancelRestores(id)).isEqualTo(1);}
            case "REFUND"->{assertThat(data.orderStatus(id)).isEqualTo("CANCELLED");assertThat(data.paymentStatus(id)).isEqualTo("REFUNDED");}
            case "REVIEW"->{assertThat(data.orderStatus(id)).isEqualTo("PARTIALLY_COMPLETED");assertThat(count("select count(*) from dbo.reviews r join dbo.review_replies p on p.review_id=r.review_id where r.order_id=? and r.status='HIDDEN' and p.staff_user_id=?",id,userId(STAFF_THUDUC))).isEqualTo(1);}
        }
    }
}

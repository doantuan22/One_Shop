package com.oneshop;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/** All current MVC mappings, actual embedded Tomcat and SQL Server, anonymous security/input probes only. */
class QaAuditRouteDatabaseIntegrationTest extends V3RegressionTestSupport {
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    @TestFactory Stream<DynamicTest> anonymousEndpointGuards() throws Exception {
        List<Map<String,Object>> routes=new ArrayList<>();
        mappings.getHandlerMethods().forEach((mapping,handler)->{
            for(String path:mapping.getPatternValues()) {
                var methods=mapping.getMethodsCondition().getMethods();
                List<String> verbs=methods.isEmpty()?List.of("GET"):methods.stream().map(Enum::name).sorted().toList();
                for(String verb:verbs) routes.add(Map.of("path",path,"verb",verb,"controller",handler.getBeanType().getName(),
                        "method",handler.getMethod().getName()));
            }
        });
        routes.add(Map.of("path","/logout","verb","POST","controller","SecurityFilterChain","method","logout"));
        routes.sort(Comparator.comparing(r->r.get("path").toString()+r.get("verb")));
        Path output=QaAuditEvidence.directory("routes");Files.createDirectories(output);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("runtime-mappings.json").toFile(),routes);
        return routes.stream().map(route->DynamicTest.dynamicTest(route.get("verb")+" "+route.get("path"),()->{
            String path=route.get("path").toString().replaceAll("\\{[^}]+}","999999999");
            // Regex-constrained path IDs remain numeric; actionPath is an unknown action, never a mutation.
            boolean post=route.get("verb").equals("POST");
            java.net.http.HttpResponse<String> response;
            int expected;
            if(post && path.startsWith("/api/auth/")) {
                var req=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+path))
                        .header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"));
                response=java.net.http.HttpClient.newHttpClient().send(req.build(),java.net.http.HttpResponse.BodyHandlers.ofString());
                expected=400;
            } else if(post) {response=send("POST",path,"");expected=403;}
            else {
                if(path.equals("/health")||path.equals("/products/suggest")) {
                    response=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
                                    java.net.URI.create("http://localhost:"+port+path)).header("Accept","application/json").GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
                } else response=send("GET",path,"");
                if(path.equals("/error")) expected=500;
                else if(path.matches("/products/[0-9]+"))expected=404;
                else if(Set.of("/","/health","/login","/register","/products","/products/suggest","/stores","/image-credits").contains(path))expected=200;
                else expected=302;
            }
            String key=route.get("verb")+"-"+route.get("path").toString().replaceAll("[^A-Za-z0-9]","_");
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve(key+".json").toFile(),
                    Map.of("route",route,"requestPath",path,"expected",expected,"actual",response.statusCode(),
                            "location",response.headers().firstValue("Location").orElse(""),"kind","anonymous guard/input probe",
                            "executedAt",java.time.Instant.now().toString()));
            assertThat(response.statusCode()).as(route.toString()).isEqualTo(expected);
            List<String> wrongRoles=path.startsWith("/admin")?List.of("khachhang1@example.com","staff.thuduc@oneshop.vn"):
                    path.startsWith("/staff")?List.of("khachhang1@example.com","admin@oneshop.vn"):
                    path.matches("/(cart|checkout|orders|reviews)(/.*)?")?List.of("staff.thuduc@oneshop.vn","admin@oneshop.vn"):List.of();
            for(String email:wrongRoles) {
                var before=checksums();
                var denied=post?postAs(email,path,"",true):getAs(email,path);
                var after=checksums();
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve(key+"-"+email.split("@")[0]+".json").toFile(),
                        Map.of("route",route,"actor",email,"expected",403,"actual",denied.statusCode(),
                                "databaseBefore",before,"databaseAfter",after,"kind","wrong-role with valid JWT and CSRF",
                                "executedAt",java.time.Instant.now().toString()));
                assertThat(denied.statusCode()).as("role denied: "+email+" "+path).isEqualTo(403);
                assertThat(after).as("denied request cannot change any table").isEqualTo(before);
            }
        }));
    }

    private Map<String,Object> checksums() {
        Map<String,Object> result=new TreeMap<>();
        for(String table:jdbc.queryForList("select name from sys.tables where is_ms_shipped=0",String.class))
            result.put(table,jdbc.queryForMap("select count_big(*) as n,checksum_agg(binary_checksum(*)) as c from dbo.["+table+"]"));
        return result;
    }
}

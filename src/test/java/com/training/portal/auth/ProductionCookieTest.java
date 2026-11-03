package com.training.portal.auth;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "server.ssl.enabled=true","server.ssl.key-store=file:${STAGE2_TLS_KEY_STORE}","server.ssl.key-store-password=test-only-tls-password","server.ssl.key-store-type=PKCS12","training.portal.frontend-origin=https://localhost:3000"})
@ActiveProfiles("prod")
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named="STAGE2_TLS_KEY_STORE",matches=".+")
class ProductionCookieTest {
 @LocalServerPort int port;@Autowired MockMvc mvc;
 @Test void tlsIssuesSecureHttpOnlySameSiteAndClientDoesNotSendCookieOverHttp()throws Exception{
  var ks=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(Path.of(System.getenv("STAGE2_TLS_KEY_STORE")))){ks.load(in,"test-only-tls-password".toCharArray());}
  var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(ks);var ssl=SSLContext.getInstance("TLS");ssl.init(null,tm.getTrustManagers(),null);
  var jar=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
  try(var client=HttpClient.newBuilder().sslContext(ssl).cookieHandler(jar).build()){
   var response=client.send(HttpRequest.newBuilder(URI.create("https://localhost:"+port+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
   assertThat(response.statusCode()).isEqualTo(200);assertThat(response.headers().allValues("set-cookie")).anySatisfy(c->assertThat(c).contains("JSESSIONID=","Secure","HttpOnly","SameSite=Lax"));
   assertThat(jar.getCookieStore().getCookies()).anySatisfy(c->assertThat(c.getSecure()).isTrue());
   var sent=new AtomicReference<String>();var plain=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
   plain.createContext("/",exchange->{sent.set(exchange.getRequestHeaders().getFirst("Cookie"));exchange.sendResponseHeaders(204,-1);exchange.close();});plain.start();
   try{client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+plain.getAddress().getPort()+"/")).GET().build(),HttpResponse.BodyHandlers.discarding());assertThat(sent.get()).isNull();}finally{plain.stop(0);}
  }
 }
 @Test void forwardedHttpsHeaderDoesNotBypassTransportRequirement()throws Exception{
  mvc.perform(get("/api/auth/csrf").header("X-Forwarded-Proto","https").header("Forwarded","proto=https;for=198.51.100.1"))
    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("HTTPS_REQUIRED")).andExpect(header().doesNotExist("Set-Cookie"));
 }

 @Autowired com.training.portal.member.KenshuMemberService members;
 @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
 @Test void productionLoginRotatesCookieAndRetainsSecureAttributes() throws Exception {
  assertThat(jdbc.queryForObject("select inet_server_port()",Integer.class)).isNotEqualTo(5432);
  String id="P"+java.util.UUID.randomUUID().toString().substring(0,7);
  members.create(new com.training.portal.member.dto.MemberCreateRequest(id,(short)2,"TLS合成講師",null,"tls-synthetic-secret"),id);
  try {
   var ks=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(Path.of(System.getenv("STAGE2_TLS_KEY_STORE")))){ks.load(in,"test-only-tls-password".toCharArray());}
   var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(ks);var ssl=SSLContext.getInstance("TLS");ssl.init(null,tm.getTrustManagers(),null);
   var jar=new CookieManager(null,CookiePolicy.ACCEPT_ALL);var mapper=new tools.jackson.databind.json.JsonMapper();
   try(var client=HttpClient.newBuilder().sslContext(ssl).cookieHandler(jar).build()) {
    var base="https://localhost:"+port;
    var csrf=mapper.readTree(client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
    var old=jar.getCookieStore().getCookies().getFirst().getValue();
    var login=client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/login")).header("Content-Type","application/json").header(csrf.get("headerName").asText(),csrf.get("token").asText())
      .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(java.util.Map.of("syainId",id,"password","tls-synthetic-secret")))).build(),HttpResponse.BodyHandlers.ofString());
    assertThat(login.statusCode()).isEqualTo(200);
    assertThat(login.headers().allValues("Set-Cookie")).anySatisfy(c->assertThat(c).contains("Secure","HttpOnly","SameSite=Lax"));
    assertThat(jar.getCookieStore().getCookies().getFirst().getValue()).isNotEqualTo(old);
    var me=client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/me")).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(me.statusCode()).isEqualTo(200);
   }
  } finally {jdbc.update("delete from kenshu_member where syain_id=?",id);}
 }
}

package com.training.portal.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;
class HttpsPolicyTest {
 MockEnvironment prod(){var e=new MockEnvironment();e.setActiveProfiles("prod");return e.withProperty("training.portal.require-https","true").withProperty("server.servlet.session.cookie.secure","true").withProperty("server.servlet.session.cookie.http-only","true").withProperty("training.portal.frontend-origin","https://training.example.invalid");}
 @Test void productionRejectsUnsafeOverrides(){
  for(var key:new String[]{"training.portal.require-https","server.servlet.session.cookie.secure","server.servlet.session.cookie.http-only"})assertThatThrownBy(()->new ProductionSecurityPolicy(prod().withProperty(key,"false"))).isInstanceOf(IllegalArgumentException.class);
  for(var strategy:new String[]{"native","framework"})assertThatThrownBy(()->new ProductionSecurityPolicy(prod().withProperty("server.forward-headers-strategy",strategy))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new ProductionSecurityPolicy(prod().withProperty("training.portal.frontend-origin","http://localhost"))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void httpCannotBeMadeSecureByForwardedHeaders()throws Exception{var req=new MockHttpServletRequest("GET","/api/auth/csrf");req.addHeader("Forwarded","for=198.51.100.1;proto=https");req.addHeader("X-Forwarded-Proto","https");req.addHeader("X-Forwarded-For","198.51.100.1");var res=new MockHttpServletResponse();var chain=new MockFilterChain();new HttpsOnlyFilter(new ProductionSecurityPolicy(prod()).httpsRequired()).doFilter(req,res,chain);assertThat(res.getStatus()).isEqualTo(403);assertThat(res.getContentAsString()).contains("HTTPS_REQUIRED");assertThat(chain.getRequest()).isNull();assertThat(res.getHeaders("Set-Cookie")).isEmpty();}
 @Test void actualSecureTransportIsAccepted()throws Exception{var req=new MockHttpServletRequest();req.setSecure(true);var chain=new MockFilterChain();new HttpsOnlyFilter(true).doFilter(req,new MockHttpServletResponse(),chain);assertThat(chain.getRequest()).isSameAs(req);}
 @Test void localHttpRemainsAvailable()throws Exception{var req=new MockHttpServletRequest();var chain=new MockFilterChain();new HttpsOnlyFilter(new ProductionSecurityPolicy(new MockEnvironment()).httpsRequired()).doFilter(req,new MockHttpServletResponse(),chain);assertThat(chain.getRequest()).isSameAs(req);}
}

package com.training.portal.auth;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import com.training.portal.member.*;
import com.training.portal.member.dto.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"training.portal.login-limit.account-attempts=2","training.portal.login-limit.source-attempts=3","training.portal.login-limit.block-seconds=1"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Transactional
class LoginLimitApiTest {
 @Autowired MockMvc mvc;@Autowired KenshuMemberService members;
 String id,source;
 @BeforeEach void setup(){var key=UUID.randomUUID().toString().substring(0,6);id="I"+key;source="2001:db8::"+key.substring(0,4);members.create(new MemberCreateRequest(id,(short)2,"制限試験",null,"synthetic-secret"),id);}
 org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder attempt(String user,String ip){return post("/api/auth/login").with(csrf()).with(r->{r.setRemoteAddr(ip);return r;}).contentType("application/json").content("{\"syainId\":\""+user+"\",\"password\":\"wrong-secret\"}");}
 @Test void knownAndUnknownAccountsHaveSameFailureAndLimitResponses()throws Exception{
  for(String user:List.of(id,"U"+id.substring(1))){String ip=source+(user.startsWith("U")?":2":":1");
   for(int i=0;i<2;i++)mvc.perform(attempt(user,ip)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
   mvc.perform(attempt(user,ip)).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED")).andExpect(header().exists("Retry-After"));
  }
 }
 @Test void sourceLimitCannotBeBypassedByChangingAccountOrForwardedHeaders()throws Exception{
  for(int i=0;i<3;i++)mvc.perform(attempt("X"+i+id.substring(1),source).header("X-Forwarded-For","198.51.100."+i)).andExpect(status().isUnauthorized());
  mvc.perform(attempt("Z"+id.substring(1),source).header("Forwarded","for=203.0.113.1")).andExpect(status().isTooManyRequests());
  mvc.perform(attempt("Z"+id.substring(1),source+":1")).andExpect(status().isUnauthorized());
 }
 @Test void csrfIsCheckedBeforeLoginAdmission()throws Exception{
  for(int i=0;i<5;i++)mvc.perform(post("/api/auth/login").with(r->{r.setRemoteAddr(source);return r;}).contentType("application/json").content("{\"syainId\":\""+id+"\",\"password\":\"wrong\"}")).andExpect(status().isForbidden());
  mvc.perform(attempt(id,source)).andExpect(status().isUnauthorized());
 }
 @Test void validLoginWithinBudgetKeepsSessionRotation()throws Exception{
  var result=mvc.perform(attempt(id,source).content("{\"syainId\":\""+id+"\",\"password\":\"synthetic-secret\"}")).andExpect(status().isOk()).andReturn();
  mvc.perform(get("/api/auth/me").session((org.springframework.mock.web.MockHttpSession)result.getRequest().getSession())).andExpect(status().isOk());
 }
 @Test void parallelAttemptsReserveBeforePasswordVerification()throws Exception{
  String unknown="U"+id.substring(1);var pool=Executors.newFixedThreadPool(8);var gate=new CountDownLatch(1);var jobs=new ArrayList<Future<Integer>>();
  try{
   for(int i=0;i<8;i++){final int n=i;jobs.add(pool.submit(()->{gate.await();return mvc.perform(attempt(unknown,source+":"+(n+1))).andReturn().getResponse().getStatus();}));}
   gate.countDown();var statuses=new ArrayList<Integer>();for(var j:jobs)statuses.add(j.get(10,TimeUnit.SECONDS));assertThat(statuses.stream().filter(x->x==401).count()).isEqualTo(2);assertThat(statuses.stream().filter(x->x==429).count()).isEqualTo(6);
  }finally{pool.shutdownNow();}
 }

 @Test void correctLoginWorksAfterTemporaryAccountBlockExpires()throws Exception{
  for(int i=0;i<2;i++)mvc.perform(attempt(id,source)).andExpect(status().isUnauthorized());
  mvc.perform(attempt(id,source+":2")).andExpect(status().isTooManyRequests());
  Thread.sleep(1100);
  mvc.perform(attempt(id,source+":3").content("{\"syainId\":\""+id+"\",\"password\":\"synthetic-secret\"}")).andExpect(status().isOk());
 }
}

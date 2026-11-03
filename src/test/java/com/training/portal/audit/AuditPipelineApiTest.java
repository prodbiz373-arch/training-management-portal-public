package com.training.portal.audit;

import java.util.*;
import java.util.concurrent.*;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import com.training.portal.member.*;
import com.training.portal.member.dto.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Transactional
class AuditPipelineApiTest {
 @Autowired MockMvc mvc;@Autowired KenshuMemberService members;
 JsonMapper json=new JsonMapper();
 record Client(MockHttpSession session,String token){}
 Client csrf(MockHttpSession session)throws Exception{var request=get("/api/auth/csrf");if(session!=null)request.session(session);var result=mvc.perform(request).andExpect(status().isOk()).andReturn();return new Client((MockHttpSession)result.getRequest().getSession(),json.readTree(result.getResponse().getContentAsString()).get("token").asText());}
 void login(Client c,String id,String password,int status)throws Exception{mvc.perform(post("/api/auth/login").session(c.session()).header("X-CSRF-TOKEN",c.token()).contentType("application/json").content(json.writeValueAsString(Map.of("syainId",id,"password",password)))).andExpect(status().is(status));}
 @Test void eventsContainNoCredentialsIdentifiersHeadersOrRequestBodies()throws Exception{
  var queue=new ConcurrentLinkedQueue<String>();var appender=new AppenderBase<ILoggingEvent>(){protected void append(ILoggingEvent e){queue.add(e.getFormattedMessage());}};appender.start();var logger=(Logger)LoggerFactory.getLogger("SECURITY_AUDIT");logger.addAppender(appender);
  String id="A"+UUID.randomUUID().toString().substring(0,7),child="C"+id.substring(1),unknown="U"+id.substring(1);
  String password="audit-password-canary-987";
  try{
   members.create(new MemberCreateRequest(id,(short)2,"Private Name Canary",null,password),id);
   var client=csrf(null);String originalToken=client.token();login(client,id,password,200);client=csrf(client.session());
   mvc.perform(post("/api/members").session(client.session()).header("X-CSRF-TOKEN",client.token()).contentType("application/json").content(json.writeValueAsString(new MemberCreateRequest(child,(short)1,"Private Child Canary",null,password)))).andExpect(status().isCreated());
   var m=members.findById(child);
   mvc.perform(put("/api/members/"+child).session(client.session()).header("X-CSRF-TOKEN",client.token()).contentType("application/json").content(json.writeValueAsString(new MemberUpdateRequest((short)1,"Changed Name Canary",null,"",m.updateCount())))).andExpect(status().isOk());
   mvc.perform(delete("/api/members/"+child).session(client.session()).header("X-CSRF-TOKEN",client.token())).andExpect(status().isNoContent());
   mvc.perform(post("/api/members").session(client.session())).andExpect(status().isForbidden());
   var limited=csrf(null);for(int i=0;i<8;i++)login(limited,unknown,password,401);login(limited,unknown,password,429);
   mvc.perform(post("/api/auth/logout").session(client.session()).header("X-CSRF-TOKEN",client.token())).andExpect(status().isNoContent());
   var stale=csrf(null);login(stale,id,password,200);var current=members.findById(id);members.update(id,new MemberUpdateRequest((short)2,current.name(),null,"changed-audit-canary",current.updateCount()),id);
   mvc.perform(get("/api/auth/me").session(stale.session())).andExpect(status().isUnauthorized());
   Set<String> expected=Set.of("LOGIN_SUCCESS","LOGIN_FAILURE","LOGIN_LIMITED","LOGOUT","SESSION_EXPIRED","ACCESS_DENIED","MEMBER_CREATED","MEMBER_UPDATED","MEMBER_DELETED");
   Set<String> seen=new HashSet<>();for(int i=0;i<200;i++){seen.clear();for(var message:queue)seen.add(json.readTree(message).get("event").asText());if(seen.containsAll(expected))break;Thread.sleep(10);}assertThat(seen).containsAll(expected);
   for(String message:queue){assertThat(message).doesNotContain(password,"changed-audit-canary",id,child,unknown,"Private Name Canary","Private Child Canary",originalToken,"JSESSIONID=","X-CSRF-TOKEN","127.0.0.1","password-canary");
    var node=json.readTree(message);assertThat(node.properties().stream().map(Map.Entry::getKey).toList()).containsExactlyInAnyOrder("schema","time","event","status","actor","source");
   }
  }finally{logger.detachAppender(appender);appender.stop();}
 }
}

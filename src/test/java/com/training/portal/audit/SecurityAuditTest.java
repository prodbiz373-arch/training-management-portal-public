package com.training.portal.audit;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SecurityAuditTest {
 @Test void emitsOnlyFixedFieldsAndDomainSeparatedPseudonyms()throws Exception{
  var events=new LinkedBlockingQueue<String>();try(var audit=new SecurityAudit(new byte[32],8,events::add)){
   String raw="employee\r\npassword=do-not-log";audit.record(SecurityAudit.Event.LOGIN_FAILURE,raw,raw,401);
   var text=events.poll(3,TimeUnit.SECONDS);assertThat(text).contains("LOGIN_FAILURE","\"status\":401").doesNotContain(raw,"do-not-log","employee","password=","\r","\n");
   var node=new tools.jackson.databind.json.JsonMapper().readTree(text);assertThat(node.get("actor").asText()).hasSize(64).isNotEqualTo(node.get("source").asText());
  }
 }
 @Test void sinkFailureDoesNotEscapeProducer()throws Exception{
  var attempted=new CountDownLatch(1);try(var audit=new SecurityAudit(new byte[32],8,event->{attempted.countDown();throw new java.io.IOException("secret-private-path");})){
   assertThatCode(()->audit.record(SecurityAudit.Event.LOGIN_SUCCESS,"person","198.51.100.1",200)).doesNotThrowAnyException();assertThat(attempted.await(3,TimeUnit.SECONDS)).isTrue();
   for(int i=0;i<100&&audit.droppedEvents()==0;i++)Thread.sleep(10);assertThat(audit.droppedEvents()).isPositive();
  }
 }
 @Test void blockedSinkCannotBlockAuthenticationAndQueueRemainsBounded()throws Exception{
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var audit=new SecurityAudit(new byte[32],1,event->{entered.countDown();release.await();});
  try{audit.record(SecurityAudit.Event.LOGIN_SUCCESS,"one","peer",200);assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
   var pool=Executors.newSingleThreadExecutor();try{pool.submit(()->{for(int i=0;i<100;i++)audit.record(SecurityAudit.Event.LOGIN_FAILURE,"two","peer",401);}).get(3,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
   assertThat(audit.droppedEvents()).isGreaterThanOrEqualTo(99);
  }finally{release.countDown();audit.close();}
 }
 @Test void unavailableHmacConfigurationUsesEphemeralKeyWithoutExposingValue(){try(var audit=new SecurityAudit("not-a-base64-secret",2)){assertThatCode(()->audit.record(SecurityAudit.Event.LOGOUT,null,null,204)).doesNotThrowAnyException();}}
}

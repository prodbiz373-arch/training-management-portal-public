package com.training.portal.setup;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import com.training.portal.member.dto.MemberCreateRequest;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest
@Import(InitialInstructorService.class)
@EnabledIfEnvironmentVariable(named="INTEGRATION_TEST_PORT",matches="59455")
class InitialInstructorConcurrencyTest {
 @Autowired InitialInstructorService setup;
 @Autowired JdbcTemplate jdbc;
 @Test void onlyOneCommittedInitialInstructorIsCreated() throws Exception {
  assertThat(jdbc.queryForObject("select inet_server_port()",Integer.class)).isEqualTo(59455);
  assertThat(jdbc.queryForObject("select current_schema()",String.class)).isEqualTo("test1");
  assertThat(jdbc.queryForObject("select count(*) from kenshu_member",Integer.class)).isZero();
  var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try {
   var tasks=new java.util.ArrayList<Future<Boolean>>();
   for(String id:java.util.List.of("BOOT_A","BOOT_B"))tasks.add(pool.submit(()->{
    start.await();try{setup.create(new MemberCreateRequest(id,(short)2,"合成講師",null,"test-only-secret"));return true;}
    catch(IllegalStateException expected){return false;}
   }));
   start.countDown();int success=0;for(var task:tasks)if(task.get(10,TimeUnit.SECONDS))success++;
   assertThat(success).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from kenshu_member",Integer.class)).isEqualTo(1);
  } finally {pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();jdbc.update("delete from kenshu_member where syain_id in ('BOOT_A','BOOT_B')");}
 }
}

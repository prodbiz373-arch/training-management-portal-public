package com.training.portal.auth;

import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.training.portal.member.*;
import com.training.portal.member.dto.*;
import static org.assertj.core.api.Assertions.*;

/** Commits on separate connections; only the explicitly selected isolated cluster is allowed. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named="INTEGRATION_TEST_PORT", matches="59455")
class RoleSessionConcurrencyTest {
    @Autowired RoleSessionGuard guard;
    @Autowired KenshuMemberService members;
    @Autowired JdbcTemplate jdbc;
    String id;
    ExecutorService pool;
    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("select inet_server_port()",Integer.class)).isEqualTo(59455);
        assertThat(jdbc.queryForObject("select current_schema()",String.class)).isEqualTo("test1");
        id="C"+UUID.randomUUID().toString().substring(0,7);
        members.create(new MemberCreateRequest(id,(short)2,"競合検証",null,"isolated-test"),id);
        pool=Executors.newFixedThreadPool(2);
    }
    @AfterEach void cleanup() throws Exception {
        pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();
        jdbc.update("delete from kenshu_member where syain_id=?",id);
    }
    void change() {
        var m=members.findById(id);
        members.update(id,new MemberUpdateRequest((short)1,m.name(),null,"",m.updateCount()),id);
    }
    void await(CountDownLatch latch) {
        try { if(!latch.await(5,TimeUnit.SECONDS)) throw new AssertionError("同期タイムアウト"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    @Test void committedRoleChangeRejectsWaitingOldWriter() throws Exception {
        var changed=new CountDownLatch(1); var release=new CountDownLatch(1); var attempting=new CountDownLatch(1);
        var auth=UsernamePasswordAuthenticationToken.authenticated(new MemberPrincipal(id,"unused",(short)2,0),null,
                new MemberPrincipal(id,"unused",(short)2,0).getAuthorities());
        var role=pool.submit(()->guard.write(()->{change();changed.countDown();await(release);}));
        await(changed);
        var writer=pool.submit(()->{attempting.countDown();guard.write(()->{
            guard.verify(auth);
            jdbc.update("update kenshu_member set name='禁止された更新' where syain_id=?",id);
        });});
        await(attempting); release.countDown(); role.get(10,TimeUnit.SECONDS);
        assertThatThrownBy(()->writer.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(RoleSessionGuard.StaleSession.class);
        assertThat(members.findById(id).name()).isEqualTo("競合検証");
        assertThat(jdbc.queryForObject("select auth_version from kenshu_member where syain_id=?",Long.class,id)).isEqualTo(1L);
    }
    @Test void existingWriteCommitsBeforeRoleChange() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        var writer=pool.submit(()->guard.write(()->{
            jdbc.update("update kenshu_member set name='先行した更新' where syain_id=?",id);
            entered.countDown();await(release);
        }));
        await(entered);
        var role=pool.submit(()->{attempting.countDown();guard.write(this::change);});
        await(attempting);release.countDown();writer.get(10,TimeUnit.SECONDS);role.get(10,TimeUnit.SECONDS);
        assertThat(members.findById(id).name()).isEqualTo("先行した更新");
        assertThat(members.findById(id).syainKbn()).isEqualTo((short)1);
    }
    @Test void simultaneousDemotionsCannotRemoveLastEditableInstructor() throws Exception {
        String other="D"+id.substring(1), trainee="T"+id.substring(1), schedule="S"+id.substring(1);
        try {
            members.create(new MemberCreateRequest(other,(short)2,"競合講師",null,"isolated-test"),id);
            members.create(new MemberCreateRequest(trainee,(short)1,"競合新人",null,"isolated-test"),id);
            jdbc.update("""
                    insert into schedule(schedule_id,schedule_name,start_date,end_date,nohol_flg,screen_id,
                    update_count,create_user_cd,create_date,update_user_cd,update_date)
                    values (?,'並行制約',current_date,current_date,0,'TEST',0,'TEST',current_timestamp,'TEST',current_timestamp)
                    """,schedule);
            for(String member:java.util.List.of(id,other,trainee)) jdbc.update("""
                    insert into schedule_can_edit(schedule_id,syain_id,syain_kbn,can_edit,pin_flg,screen_id,
                    update_count,create_user_cd,create_date,update_user_cd,update_date)
                    values (?,?,?,1,0,'TEST',0,'TEST',current_timestamp,'TEST',current_timestamp)
                    """,schedule,member,member.equals(trainee)?1:2);
            var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
            java.util.List<Future<Boolean>> results=new java.util.ArrayList<>();
            for(String target:java.util.List.of(id,other)) results.add(pool.submit(()->{
                ready.countDown();await(start);
                try {
                    guard.write(()->members.update(target,new MemberUpdateRequest((short)1,"区分変更",null,"",0),id));
                    return true;
                } catch(com.training.portal.member.exception.MemberRoleChangeNotAllowedException expected) { return false; }
            }));
            await(ready);start.countDown();int successes=0;
            for(var result:results) if(result.get(10,TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from schedule_can_edit where schedule_id=? and syain_kbn=2 and can_edit=1",Integer.class,schedule)).isEqualTo(1);
        } finally {
            jdbc.update("delete from schedule_can_edit where schedule_id=?",schedule);
            jdbc.update("delete from schedule where schedule_id=?",schedule);
            jdbc.update("delete from kenshu_member where syain_id in (?,?)",other,trainee);
        }
    }

    @Test void committedPasswordChangeRejectsWaitingOldWriter() throws Exception {
        var changed=new CountDownLatch(1); var release=new CountDownLatch(1); var attempting=new CountDownLatch(1);
        var auth=UsernamePasswordAuthenticationToken.authenticated(new MemberPrincipal(id,"unused",(short)2,0),null,
                new MemberPrincipal(id,"unused",(short)2,0).getAuthorities());
        var role=pool.submit(()->guard.write(()->{var m=members.findById(id);members.update(id,new MemberUpdateRequest((short)2,m.name(),null,"new-concurrent-secret",m.updateCount()),id);changed.countDown();await(release);}));
        await(changed);
        var writer=pool.submit(()->{attempting.countDown();guard.write(()->{
            guard.verify(auth);
            jdbc.update("update kenshu_member set name='禁止された更新' where syain_id=?",id);
        });});
        await(attempting); release.countDown(); role.get(10,TimeUnit.SECONDS);
        assertThatThrownBy(()->writer.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(RoleSessionGuard.StaleSession.class);
        assertThat(members.findById(id).name()).isEqualTo("競合検証");
        assertThat(jdbc.queryForObject("select auth_version from kenshu_member where syain_id=?",Long.class,id)).isEqualTo(1L);
    }

    @Test void concurrentPasswordUpdatesHaveOneWinnerAndOneGeneration() throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var jobs=new java.util.ArrayList<Future<Boolean>>();
        for(int i=0;i<2;i++){final int n=i;jobs.add(pool.submit(()->{ready.countDown();await(start);try{guard.write(()->members.update(id,new MemberUpdateRequest((short)2,"競合検証",null,"new-password-"+n,0),id));return true;}catch(com.training.portal.member.exception.MemberUpdateConflictException expected){return false;}}));}
        await(ready);start.countDown();int successes=0;for(var job:jobs)if(job.get(10,TimeUnit.SECONDS))successes++;assertThat(successes).isEqualTo(1);
        assertThat(jdbc.queryForObject("select auth_version from kenshu_member where syain_id=?",Long.class,id)).isEqualTo(1L);
    }
}

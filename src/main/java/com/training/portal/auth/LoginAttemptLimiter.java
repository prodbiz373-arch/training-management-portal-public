package com.training.portal.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Single-node, bounded admission control. No password, session token or forwarded header is stored. */
@Component
public final class LoginAttemptLimiter {
    private final int accountLimit, sourceLimit, maxKeys, maxConcurrent;
    private final long accountWindow, sourceWindow, block;
    private final LongSupplier clock;
    private final Map<String, Bucket> accounts = new HashMap<>(), sources = new HashMap<>();
    private int pending;
    private long nextCleanup;
    @org.springframework.beans.factory.annotation.Autowired
    public LoginAttemptLimiter(
            @Value("${training.portal.login-limit.account-attempts:8}") int accountLimit,
            @Value("${training.portal.login-limit.source-attempts:200}") int sourceLimit,
            @Value("${training.portal.login-limit.account-window-seconds:300}") long accountWindow,
            @Value("${training.portal.login-limit.source-window-seconds:60}") long sourceWindow,
            @Value("${training.portal.login-limit.block-seconds:60}") long block,
            @Value("${training.portal.login-limit.max-keys:10000}") int maxKeys,
            @Value("${training.portal.login-limit.max-concurrent:32}") int maxConcurrent) {
        this(accountLimit, sourceLimit, accountWindow, sourceWindow, block, maxKeys, maxConcurrent,
                monotonicClock());
    }
    private static LongSupplier monotonicClock(){long start=System.nanoTime();return () -> (System.nanoTime()-start)/1_000_000L;}
    LoginAttemptLimiter(int accountLimit, int sourceLimit, long accountWindow, long sourceWindow,
            long block, int maxKeys, int maxConcurrent, LongSupplier clock) {
        if (accountLimit < 1 || accountLimit > 1000000 || sourceLimit < 1 || sourceLimit > 1000000 || maxKeys < 1 || maxKeys > 100000 || maxConcurrent < 1 || maxConcurrent > 1024
                || accountWindow < 1 || sourceWindow < 1 || block < 1
                || accountWindow > 86400 || sourceWindow > 86400 || block > 3600) {
            throw new IllegalArgumentException("ログイン制限設定が不正です");
        }
        this.accountLimit=accountLimit; this.sourceLimit=sourceLimit; this.maxKeys=maxKeys;
        this.maxConcurrent=maxConcurrent; this.accountWindow=accountWindow*1000;
        this.sourceWindow=sourceWindow*1000; this.block=block*1000; this.clock=clock;
    }
    public synchronized Attempt acquire(String account, String source) {
        long now=clock.getAsLong();
        if (now>=nextCleanup) {
            accounts.values().removeIf(b -> b.pending==0 && now>=Math.max(b.end,b.until));
            sources.values().removeIf(b -> b.pending==0 && now>=Math.max(b.end,b.until));
            nextCleanup=now+1000;
        }
        if (pending>=maxConcurrent) throw new Limited(1);
        // Account IDs are already DTO-validated (10 chars); remoteAddr is the transport peer.
        if (account.length()>10 || source==null || source.length()>128) throw new Limited(1);
        var ip=bucket(sources,source,sourceWindow,now);
        refresh(ip,sourceWindow,now);
        if (ip.until>now) throw limited(ip.until-now);
        if (ip.count>=sourceLimit) { ip.until=now+block; throw limited(block); }
        ip.count++; // Include successful and account-rejected attempts in source admission budget.
        var user=bucket(accounts,account,accountWindow,now);
        refresh(user,accountWindow,now);
        if (user.until>now) throw limited(user.until-now);
        if (user.count+user.pending>=accountLimit) throw new Limited(1);
        user.pending++; ip.pending++; pending++;
        return new Attempt(user,ip);
    }
    private Bucket bucket(Map<String,Bucket> map,String key,long window,long now) {
        var b=map.get(key);
        if (b==null) { if(map.size()>=maxKeys) throw new Limited(1); b=new Bucket(now+window);map.put(key,b); }
        return b;
    }
    private void refresh(Bucket b,long window,long now) {
        if(b.pending==0 && ((b.until!=0 && now>=b.until) || (b.until==0 && now>=b.end))) {
            b.count=0;b.until=0;b.end=now+window;
        }
    }
    private Limited limited(long millis) { return new Limited(Math.max(1,(millis+999)/1000)); }
    private static final class Bucket { int count,pending; long end,until; Bucket(long end){this.end=end;} }
    public final class Attempt implements AutoCloseable {
        private final Bucket user,ip; private boolean success,closed;
        private Attempt(Bucket user,Bucket ip){this.user=user;this.ip=ip;}
        public void success(){this.success=true;}
        @Override public void close(){
            synchronized(LoginAttemptLimiter.this){
                if(closed)return;closed=true;user.pending--;ip.pending--;pending--;
                if(success){user.count=0;user.until=0;}
                else if(++user.count>=accountLimit && user.until==0) user.until=clock.getAsLong()+block;
            }
        }
    }
    public static final class Limited extends RuntimeException {
        private final long retryAfter;
        Limited(long retryAfter){super("しばらく待ってから再試行してください");this.retryAfter=retryAfter;}
        public long retryAfter(){return retryAfter;}
    }
}

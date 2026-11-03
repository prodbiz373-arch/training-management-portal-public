package com.training.portal.auth;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LoginAttemptLimiterTest {
    AtomicLong time=new AtomicLong();
    LoginAttemptLimiter limiter(int accounts,int source,int keys,int concurrent){return new LoginAttemptLimiter(accounts,source,300,60,10,keys,concurrent,time::get);}
    @Test void accountBlocksAcrossSourcesAndExpiresWithoutExtendingOnDeniedAttempts(){
        var l=limiter(2,100,10,10);
        l.acquire("known","a").close();l.acquire("known","b").close();
        assertThatThrownBy(()->l.acquire("known","c")).isInstanceOf(LoginAttemptLimiter.Limited.class);
        time.set(9000);assertThatThrownBy(()->l.acquire("known","d")).isInstanceOf(LoginAttemptLimiter.Limited.class);
        time.set(10001);try(var t=l.acquire("known","e")){t.success();}
    }
    @Test void sourceBudgetIncludesSuccessfulRequestsAcrossAccounts(){
        var l=limiter(10,2,10,10);
        for(var id:List.of("one","two"))try(var t=l.acquire(id,"same")){t.success();}
        assertThatThrownBy(()->l.acquire("three","same")).isInstanceOf(LoginAttemptLimiter.Limited.class);
        try(var t=l.acquire("three","other")){t.success();}
        time.set(10001);try(var t=l.acquire("three","same")){t.success();}
    }
    @Test void successesResetAccountFailuresButNotSourceBudget(){
        var l=limiter(2,100,10,10);l.acquire("one","a").close();
        try(var t=l.acquire("one","a")){t.success();}
        l.acquire("one","a").close();try(var t=l.acquire("one","a")){t.success();}
    }
    @Test void inFlightReservationsCannotBypassAccountBudget()throws Exception{
        var l=limiter(3,100,100,20);var permits=new ConcurrentLinkedQueue<LoginAttemptLimiter.Attempt>();
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(12);
        try{
            var jobs=new ArrayList<Future<?>>();
            for(int i=0;i<12;i++){final int n=i;jobs.add(pool.submit(()->{try{start.await();permits.add(l.acquire("shared","ip"+n));}catch(LoginAttemptLimiter.Limited expected){}catch(InterruptedException e){throw new RuntimeException(e);}}));}
            start.countDown();for(var j:jobs)j.get(5,TimeUnit.SECONDS);assertThat(permits).hasSize(3);
        }finally{permits.forEach(LoginAttemptLimiter.Attempt::close);pool.shutdownNow();}
    }
    @Test void globalAdmissionAndMapCapacityAreBoundedAndRecover(){
        var l=limiter(4,100,2,1);var held=l.acquire("one","a");
        assertThatThrownBy(()->l.acquire("two","b")).isInstanceOf(LoginAttemptLimiter.Limited.class);
        held.success();held.close();held.close(); // closing a reservation twice must not release another slot
        try(var t=l.acquire("two","b")){t.success();}
        assertThatThrownBy(()->l.acquire("three","c")).isInstanceOf(LoginAttemptLimiter.Limited.class);
        time.set(301000);try(var t=l.acquire("three","c")){t.success();}
    }
    @Test void expiryDoesNotEvictActiveReservations(){
        var l=limiter(1,100,10,10);var held=l.acquire("one","a");time.set(400000);
        assertThatThrownBy(()->l.acquire("one","b")).isInstanceOf(LoginAttemptLimiter.Limited.class);held.close();
    }
    @Test void invalidPoliciesAreRejected(){assertThatThrownBy(()->limiter(0,10,10,2)).isInstanceOf(IllegalArgumentException.class);}
}

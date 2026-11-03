package com.training.portal.audit;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;

/** Bounded best-effort audit transport. Only fixed enums, numbers and keyed pseudonyms leave this class. */
@org.springframework.jmx.export.annotation.ManagedResource(objectName="training.portal:type=SecurityAudit")
public final class SecurityAudit implements AutoCloseable {
    public enum Event { LOGIN_SUCCESS, LOGIN_FAILURE, LOGIN_LIMITED, LOGOUT, SESSION_EXPIRED,
        ACCESS_DENIED, AUTHENTICATION_REQUIRED, MEMBER_CREATED, MEMBER_UPDATED, MEMBER_DELETED }
    @FunctionalInterface public interface Sink { void write(String safeJson) throws Exception; }
    private final ArrayBlockingQueue<String> queue;
    private final Sink sink;
    private final byte[] key;
    private final Thread worker;
    private final AtomicLong dropped=new AtomicLong();
    private volatile boolean running=true;
    public SecurityAudit(String configuredKey,int capacity) {
        this(readKey(configuredKey),capacity,message->LoggerFactory.getLogger("SECURITY_AUDIT").info(message));
    }
    public SecurityAudit(byte[] key,int capacity,Sink sink) {
        this.key=key.clone();this.sink=sink;
        queue=new ArrayBlockingQueue<>(Math.max(1,Math.min(100000,capacity)));
        worker=new Thread(this::drain,"security-audit-writer");worker.setDaemon(true);worker.start();
    }
    private static byte[] readKey(String value) {
        if(value!=null && !value.isBlank()) {
            try { var decoded=Base64.getDecoder().decode(value);if(decoded.length>=32)return decoded; }
            catch(IllegalArgumentException ignored) { /* Never print key/config values. */ }
        }
        var key=new byte[32];new SecureRandom().nextBytes(key);return key;
    }
    private String pseudonym(String domain,String value) throws Exception {
        if(value==null || value.isBlank())return "-";
        if(value.length()>256)return "-"; // Not a place to accept arbitrary request payloads.
        var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((domain+"\0"+value).getBytes(StandardCharsets.UTF_8)));
    }
    public void record(Event event,String actor,String source,int status) {
        try {
            if(!running || event==null || status<100 || status>599) { dropped.incrementAndGet();return; }
            String json="{\"schema\":1,\"time\":"+System.currentTimeMillis()+",\"event\":\""+event.name()
                    +"\",\"status\":"+status+",\"actor\":\""+pseudonym("actor",actor)
                    +"\",\"source\":\""+pseudonym("source",source)+"\"}";
            if(!queue.offer(json))dropped.incrementAndGet();
        } catch(Exception ignored) { dropped.incrementAndGet(); }
    }
    private void drain() {
        while(running || !queue.isEmpty()) {
            try {var event=queue.poll(100,TimeUnit.MILLISECONDS);if(event!=null)try{sink.write(event);}catch(Exception ignored){dropped.incrementAndGet();}}
            catch(InterruptedException ignored){if(!running)break;}
        }
    }
    /** Observable without exposing events; monitoring integration is deployment-specific. */
    public long droppedEvents(){return dropped.get();}
    @org.springframework.jmx.export.annotation.ManagedAttribute
    public long getDroppedEvents(){return dropped.get();}
    @Override @PreDestroy public void close(){running=false;try{worker.join(1000);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}if(worker.isAlive())worker.interrupt();}
}

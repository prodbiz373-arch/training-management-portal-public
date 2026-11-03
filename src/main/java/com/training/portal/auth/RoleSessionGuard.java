package com.training.portal.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RoleSessionGuard {
    // Shared by all application API writes, across processes using the same database.
    private static final long WRITE_LOCK = 0x545241494E494E47L;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public RoleSessionGuard(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setTimeout(30);
    }
    public void verify(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof MemberPrincipal principal)) throw new StaleSession();
        var rows = jdbc.query("select syain_kbn, auth_version from kenshu_member where syain_id=?",
                (rs, index) -> rs.getShort(1) == principal.getMemberType()
                        && rs.getLong(2) == principal.getAuthVersion(), authentication.getName());
        String expected = principal.getMemberType() == 2 ? "ROLE_INSTRUCTOR" : "ROLE_TRAINEE";
        if (rows.size() != 1 || !rows.get(0) || authentication.getAuthorities().stream().anyMatch(a ->
                        !expected.equals(a.getAuthority()) && !"FACTOR_PASSWORD".equals(a.getAuthority()))
                || authentication.getAuthorities().stream().noneMatch(a -> expected.equals(a.getAuthority()))) {
            throw new StaleSession();
        }
    }
    public void lockWrites() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("更新トランザクションが必要です");
        }
        jdbc.execute("SET LOCAL lock_timeout = '5s'");
        jdbc.execute("SELECT pg_advisory_xact_lock(" + WRITE_LOCK + ")");
    }
    public void write(Runnable action) {
        transactions.executeWithoutResult(status -> {
            lockWrites();
            action.run();
        });
    }
    public static final class StaleSession extends RuntimeException {
        public StaleSession() { super("再ログインが必要です"); }
    }
}

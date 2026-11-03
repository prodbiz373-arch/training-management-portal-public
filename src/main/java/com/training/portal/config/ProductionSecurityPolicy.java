package com.training.portal.config;

import org.springframework.core.env.Environment;

public final class ProductionSecurityPolicy {
    private final boolean httpsRequired;
    public ProductionSecurityPolicy(Environment env) {
        boolean prod=env.matchesProfiles("prod");
        httpsRequired=env.getProperty("training.portal.require-https",Boolean.class,prod);
        if(prod && (!httpsRequired || !env.getProperty("server.servlet.session.cookie.secure",Boolean.class,false)
                || !env.getProperty("server.servlet.session.cookie.http-only",Boolean.class,false)
                || !"none".equalsIgnoreCase(env.getProperty("server.forward-headers-strategy","none")))) {
            throw new IllegalArgumentException("本番のHTTPS・Cookie・転送ヘッダー設定が不正です");
        }
        if (prod) {
            var origin=java.net.URI.create(env.getProperty("training.portal.frontend-origin",""));
            if (!"https".equalsIgnoreCase(origin.getScheme()) || origin.getHost()==null
                    || origin.getUserInfo()!=null || origin.getQuery()!=null || origin.getFragment()!=null
                    || !origin.getPath().isEmpty()) {
                throw new IllegalArgumentException("本番OriginはHTTPSのOriginで指定してください");
            }
        }
    }
    public boolean httpsRequired(){return httpsRequired;}
}

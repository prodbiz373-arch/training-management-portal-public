package com.training.portal.setup;

import java.io.Console;
import java.util.*;
import javax.sql.DataSource;
import com.training.portal.auth.RoleSessionGuard;
import com.training.portal.config.PasswordEncoderConfig;
import com.training.portal.member.*;
import com.training.portal.member.dto.MemberCreateRequest;
import com.training.portal.schedule.ScheduleCanEditRepository;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** No web server, application scan, automatic migration, or normal startup hook. */
public final class InitialInstructorCommand {
    public static void main(String[] args) {
        ((ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory())
                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.OFF);
        int exit=run(args,System.console());
        if(exit!=0) System.exit(exit);
    }
    static int run(String[] args,Console console) {
        char[] dbPassword=null,password=null,confirmation=null;
        try {
            var target=parse(args);
            if(console==null) throw new IllegalStateException();
            console.printf("接続先: %s\n",target.description());
            if(!target.description().equals(console.readLine("接続先をそのまま入力して確認: "))) throw new IllegalStateException();
            dbPassword=console.readPassword("DBパスワード（非表示）: ");
            String id=console.readLine("社員ID: ");
            String name=console.readLine("社員名: ");
            String kana=console.readLine("ふりがな（任意）: ");
            password=console.readPassword("初期講師パスワード（非表示）: ");
            confirmation=console.readPassword("初期講師パスワード再入力（非表示）: ");
            if(password==null || !Arrays.equals(password,confirmation) || dbPassword==null) throw new IllegalArgumentException();
            var data=new DriverManagerDataSource(target.url(),"training_portal_user",new String(dbPassword));
            var jdbc=new JdbcTemplate(data);
            var identity=jdbc.queryForMap("select current_database() db,current_user usr,current_schema() sch,inet_server_port() port,host(inet_server_addr()) addr");
            if(!"training_portal".equals(identity.get("db")) || !"training_portal_user".equals(identity.get("usr"))
                    || !target.schema().equals(identity.get("sch")) || !Integer.valueOf(target.port()).equals(identity.get("port"))
                    || !"127.0.0.1".equals(identity.get("addr"))) throw new IllegalStateException();
            try(var context=new AnnotationConfigApplicationContext()) {
                context.registerBean(DataSource.class,()->data);
                context.registerBean("bootstrapSchema",String.class,target::schema);
                context.register(BootstrapConfig.class);context.refresh();
                context.getBean(InitialInstructorService.class).create(new MemberCreateRequest(id,(short)2,name,
                        kana==null||kana.isBlank()?null:kana,new String(password)));
            }
            console.printf("初期講師を登録しました。ログイン画面で確認してください。\n");return 0;
        } catch(Exception ignored) {
            System.err.println("初期登録を拒否しました。接続先・空の社員テーブル・入力・V5適用を確認してください。詳細な入力値は出力しません。");return 1;
        } finally {
            for(char[] value:new char[][]{dbPassword,password,confirmation}) if(value!=null)Arrays.fill(value,'\0');
        }
    }
    record Target(int port,String schema) {
        String description(){return "training_portal/"+schema+"@127.0.0.1:"+port;}
        String url(){return "jdbc:postgresql://127.0.0.1:"+port+"/training_portal?currentSchema="+schema+"&connectTimeout=5";}
    }
    static Target parse(String[] args) {
        if(args.length!=5 || !args[0].equals("--initialize-first-instructor") || !args[1].equals("--host=127.0.0.1")
                || !args[2].startsWith("--port=") || !args[3].equals("--database=training_portal") || !args[4].startsWith("--schema=")) throw new IllegalArgumentException();
        int port=Integer.parseInt(args[2].substring(7));String schema=args[4].substring(9);
        if(port<1||port>65535||!Set.of("public","test1").contains(schema))throw new IllegalArgumentException();
        return new Target(port,schema);
    }
    // Deliberately no @Configuration/@Component: normal application scanning must not register this setup.
    @EnableJpaRepositories(basePackages="com.training.portal")
    @EnableTransactionManagement
    @Import({PasswordEncoderConfig.class,KenshuMemberService.class,MemberRoleChangeSupport.class,RoleSessionGuard.class})
    static class BootstrapConfig {
        @Bean JdbcTemplate jdbcTemplate(DataSource source){return new JdbcTemplate(source);}
        @Bean LocalValidatorFactoryBean validator(){return new LocalValidatorFactoryBean();}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source,String bootstrapSchema){
            var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);
            factory.setPackagesToScan("com.training.portal");factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.default_schema",bootstrapSchema,
                    "hibernate.show_sql","false"));return factory;
        }
        @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory){return new JpaTransactionManager(factory);}
        @Bean InitialInstructorService initialInstructorService(JdbcTemplate jdbc,KenshuMemberRepository repository,KenshuMemberService members){return new InitialInstructorService(jdbc,repository,members);}
    }
}

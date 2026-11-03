package com.training.portal.setup;

import java.io.Console;
import java.io.PrintWriter;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.logging.*;
import org.flywaydb.core.internal.license.VersionPrinter;

/** Explicit local, empty-database bootstrap. No Spring context, web server or automatic startup hook. */
public final class FreshDatabaseCommand {
    static final Map<String,String> HASHES = Map.of(
        "V1__create_tables.sql", "df787f5f24e05ff10a5414169aaa2ebbf5948161935e32b2946c72d71773454c",
        "V2__insert_issue_statuses_master_data.sql", "17c9d848675ea26ca9abd5e8acf9507b316534e20d4ae33b6fb8ded73ecb2630",
        "V3__allow_null_schedule_content.sql", "305212aec43bfed52a0f56ea5cbf526ba63de401fb0aa10867a3cf87a138cfa5",
        "V4__allow_null_schedule_content_in_public.sql", "c8c8fce7632110f4ee6a09290d058a213d3afbe5c0d06bd8c2c5c2223821dfa7",
        "V5__add_member_auth_version.sql", "a95cc12eaeb1714b6cc509cba81caff521a94a41b43a3d9ba895f78f7dd79307");
    static final Map<String,Integer> CHECKSUMS = Map.of("1",1122476101,"2",463837306,"3",-676549352,"4",1630550406,"5",1806942905);
    static final Set<String> TABLES = Set.of("kenshu_member","schedule","schedule_can_edit","schedule_bunrui",
        "schedule_details","issue_statuses","issue_submission_statuses","issue_submission_statuses_comment","flyway_schema_history");
    static final long LOCK = 0x46524553484442L;
    record Target(int port,String database,String schema,Path migrations) {
        String description(){return database+"/"+schema+"@127.0.0.1:"+port;}
        String url(){return "jdbc:postgresql://127.0.0.1:"+port+"/"+database+"?currentSchema="+schema;}
    }
    static void require(boolean value){if(!value)throw new IllegalStateException("Bootstrap rejected");}
    static Target parse(String[] args) {
        require(args.length==7 && args[0].equals("--initialize-empty-database"));
        Map<String,String> values=new HashMap<>();
        for(int i=1;i<args.length;i++) {var p=args[i].split("=",2);require(p.length==2 && values.put(p[0],p[1])==null);}
        require(values.keySet().equals(Set.of("--host","--port","--database","--schema","--user","--migrations")));
        require("127.0.0.1".equals(values.get("--host")) && "training_portal_user".equals(values.get("--user")));
        int port=Integer.parseInt(values.get("--port"));require(port>=1024 && port<=65535 && port!=5432);
        String db=values.get("--database"),schema=values.get("--schema");
        require(db.matches("[a-z][a-z0-9_]{0,62}") && !Set.of("postgres","template0","template1").contains(db));
        require(Set.of("public","test1").contains(schema));
        return new Target(port,db,schema,Path.of(values.get("--migrations")).toAbsolutePath().normalize());
    }
    static void quietLogs() {
        ((ch.qos.logback.classic.LoggerContext)org.slf4j.LoggerFactory.getILoggerFactory())
            .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.OFF);
        LogFactory.setLogCreator(cls -> new Log(){public void debug(String s){}public void info(String s){}public void warn(String s){}
            public void error(String s){}public void error(String s,Exception e){}public void notice(String s){} });
    }
    public static void main(String[] args) {quietLogs();int result=run(args,System.console());if(result!=0)System.exit(result);}
    static int run(String[] args,Console console) {
        char[] password=null;
        try {
            Target target=parse(args);require(console!=null);
            console.printf("新規専用DBだけに使用: %s\n",target.description());
            require(target.description().equals(console.readLine("接続先をそのまま入力して確認: ")));
            password=console.readPassword("DBパスワード（非表示）: ");require(password!=null && password.length>0);
            execute(target,password);return 0;
        }catch(Exception | LinkageError exception){System.err.println("STOP: 新規構築を中止しました。接続先・空DB・SQL・履歴を確認してください。自動再試行/修復は行いません。");return 1;}
        finally{if(password!=null)Arrays.fill(password,'\0');}
    }
    static Path freeze(Path source) throws Exception {
        try(var files=Files.list(source)){require(files.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(HASHES.keySet()));}
        // Verify all bytes before opening any DB connection. Use an immutable-in-process private copy for phases.
        Map<String,byte[]> bytes=new HashMap<>();
        for(var e:HASHES.entrySet()) {var p=source.resolve(e.getKey());require(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS));
            byte[] b=Files.readAllBytes(p);require(e.getValue().equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b))));bytes.put(e.getKey(),b);}
        Path temp=Files.createTempDirectory("training-fresh-sql-");
        try {for(var e:bytes.entrySet())Files.write(temp.resolve(e.getKey()),e.getValue(),StandardOpenOption.CREATE_NEW);return temp;}
        catch(Exception e){cleanup(temp);throw e;}
    }
    static void cleanup(Path p) throws Exception {if(p!=null){try(var files=Files.list(p)){for(var f:files.toList())Files.delete(f);}Files.delete(p);}}
    static String scalar(Connection c,String sql) throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(sql)){require(r.next());return r.getString(1);}}
    static final class Source implements DataSource {
        final Target target;final String password;
        Source(Target target,char[] password){this.target=target;this.password=new String(password);}
        public Connection getConnection() throws SQLException {
            Properties p=new Properties();p.setProperty("user","training_portal_user");p.setProperty("password",password);
            p.setProperty("connectTimeout","5");p.setProperty("socketTimeout","60");
            Connection c=DriverManager.getConnection(target.url(),p);
            try {
                require(target.database.equals(scalar(c,"select current_database()")));
                require("training_portal_user".equals(scalar(c,"select current_user")));
                require(target.schema.equals(scalar(c,"select current_schema()")));
                require("127.0.0.1".equals(scalar(c,"select host(inet_server_addr())")));
                require(Integer.toString(target.port).equals(scalar(c,"select inet_server_port()")));
                require(scalar(c,"show server_version_num").startsWith("17"));
                require("t".equals(scalar(c,"select not rolsuper and not rolcreatedb and not rolcreaterole and not rolreplication from pg_roles where rolname=current_user")));
                require("t".equals(scalar(c,"select pg_get_userbyid(datdba)=current_user from pg_database where datname=current_database()")));
                require("t".equals(scalar(c,"select pg_get_userbyid(nspowner) in (current_user,'pg_database_owner') from pg_namespace where nspname=current_schema()")));
                try(var s=c.createStatement()){s.execute("set lock_timeout='5s'");s.execute("set statement_timeout='30s'");}
                return c;
            }catch(Exception e){c.close();throw new SQLException("Target rejected");}
        }
        public Connection getConnection(String u,String p){throw new UnsupportedOperationException();}
        public PrintWriter getLogWriter(){return null;} public void setLogWriter(PrintWriter w){}public void setLoginTimeout(int s){}
        public int getLoginTimeout(){return 5;}public Logger getParentLogger(){return Logger.getGlobal();}
        public <T>T unwrap(Class<T> t){throw new UnsupportedOperationException();}public boolean isWrapperFor(Class<?> t){return false;}
    }
    static void empty(Connection c) throws SQLException {
        require("0".equals(scalar(c,"select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname not like 'pg_%' and n.nspname<>'information_schema'")));
        require("0".equals(scalar(c,"select count(*) from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname not like 'pg_%' and n.nspname<>'information_schema'")));
        require("0".equals(scalar(c,"select count(*) from pg_type t join pg_namespace n on n.oid=t.typnamespace where n.nspname not like 'pg_%' and n.nspname<>'information_schema'")));
        require("0".equals(scalar(c,"select count(*) from pg_extension where extname<>'plpgsql'")));
        require("0".equals(scalar(c,"select count(*) from pg_namespace where nspname not like 'pg_%' and nspname not in ('public','test1','information_schema')")));
        require("0".equals(scalar(c,"select count(*) from pg_stat_activity where datname=current_database() and backend_type='client backend' and pid<>pg_backend_pid()")));
        require("0".equals(scalar(c,"select count(*) from pg_prepared_xacts where database=current_database()")));
    }
    static Flyway phase(Source ds,Path sql,int target,boolean skip) {
        return Flyway.configure().dataSource(ds).defaultSchema(ds.target.schema).schemas(ds.target.schema).createSchemas(false)
            .locations("filesystem:"+sql).target(Integer.toString(target)).skipExecutingMigrations(skip).cleanDisabled(true)
            .baselineOnMigrate(false).outOfOrder(false).validateMigrationNaming(true).load();
    }
    static List<Integer> pending(Flyway f) {
        var all=f.info().all();require(all.length==5);
        for(var m:all){String v=m.getVersion().toString();require(CHECKSUMS.containsKey(v) && Objects.equals(CHECKSUMS.get(v),m.getChecksum()));
            require(HASHES.containsKey(m.getScript()) && m.getScript().startsWith("V"+v+"__"));}
        return Arrays.stream(f.info().pending()).map(m->Integer.valueOf(m.getVersion().toString())).toList();
    }
    static void history(Connection c,int max) throws SQLException {
        try(var s=c.createStatement();var rows=s.executeQuery("select installed_rank,version,checksum,success,script,type from flyway_schema_history order by installed_rank")) {
            int i=0;while(rows.next()){i++;String v=Integer.toString(i);require(rows.getInt(1)==i && v.equals(rows.getString(2))
                && CHECKSUMS.get(v)==rows.getInt(3) && rows.getBoolean(4) && HASHES.containsKey(rows.getString(5)) && rows.getString(5).startsWith("V"+v+"__") && "SQL".equals(rows.getString(6)));}require(i==max);
        }
    }
    static void nullable(Connection c,boolean expected) throws SQLException {
        require(Boolean.toString(expected).equals(scalar(c,"select (not attnotnull)::text from pg_attribute where attrelid='schedule_details'::regclass and attname='schedule_content' and not attisdropped")));
    }
    static void structure(Connection c) throws SQLException {
        Set<String> tables=new HashSet<>();try(var s=c.createStatement();var rows=s.executeQuery("select tablename from pg_tables where schemaname=current_schema()")){while(rows.next())tables.add(rows.getString(1));}require(tables.equals(TABLES));
        require("7".equals(scalar(c,"select count(*) from issue_statuses")));
        for(String t:TABLES)if(!Set.of("issue_statuses","flyway_schema_history").contains(t))require("0".equals(scalar(c,"select count(*) from "+t)));
        require("1".equals(scalar(c,"select count(*) from information_schema.columns where table_schema=current_schema() and table_name='kenshu_member' and column_name='auth_version' and data_type='bigint' and is_nullable='NO' and column_default='0'")));
        require("1".equals(scalar(c,"select count(*) from pg_constraint where conrelid='kenshu_member'::regclass and conname='ck_kenshu_member_auth_version' and contype='c' and convalidated and pg_get_constraintdef(oid)='CHECK ((auth_version >= 0))'")));
        require("0".equals(scalar(c,"select count(*) from pg_tables where schemaname=current_schema() and tableowner<>current_user")));
    }
    static void execute(Target target,char[] password) throws Exception {
        require("12.4.0".equals(VersionPrinter.getVersion()));Path frozen=freeze(target.migrations);
        try {
            var ds=new Source(target,password);
            try(var c=ds.getConnection()) {
                c.setReadOnly(true);empty(c);require("t".equals(scalar(c,"select pg_try_advisory_lock("+LOCK+")")));empty(c);
                c.setReadOnly(false);
                if(target.schema.equals("public")) {
                    apply(ds,frozen,c,2,false,List.of(1,2),0);nullable(c,false);
                    apply(ds,frozen,c,3,true,List.of(3),2);nullable(c,false); // proves V3 SQL did not run
                    apply(ds,frozen,c,4,false,List.of(4),3);nullable(c,true);
                    apply(ds,frozen,c,5,false,List.of(5),4);
                }else {apply(ds,frozen,c,5,false,List.of(1,2,3,4,5),0);nullable(c,true);}
                history(c,5);structure(c);var last=phase(ds,frozen,5,false);last.validate();require(pending(last).isEmpty());
                System.out.println("PASS: V1-V5 history/checksums, schema, ownership, empty business data; validate PASS pending=[]");
            }
        }finally{cleanup(frozen);}
    }
    static void apply(Source ds,Path sql,Connection c,int target,boolean skip,List<Integer> expected,int before) throws SQLException {
        if(before>0)history(c,before);
        var f=phase(ds,sql,target,skip);require(pending(f).equals(expected));
        System.out.println("phase target="+target+" skip="+skip+" pending="+expected);
        var result=f.migrate();require(result.success && result.migrationsExecuted==expected.size());f.validate();history(c,target);
        System.out.println("phase PASS target="+target);
    }
}

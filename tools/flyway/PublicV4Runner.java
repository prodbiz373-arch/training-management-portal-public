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

/** Dedicated maintenance runner. Default mode is read-only; never starts Spring Boot. */
public class PublicV4Runner {
    static final String DB = "training_portal", SCHEMA = "public";
    static final Map<String, String> HASHES = Map.of(
        "V1__create_tables.sql", "df787f5f24e05ff10a5414169aaa2ebbf5948161935e32b2946c72d71773454c",
        "V2__insert_issue_statuses_master_data.sql", "17c9d848675ea26ca9abd5e8acf9507b316534e20d4ae33b6fb8ded73ecb2630",
        "V3__allow_null_schedule_content.sql", "305212aec43bfed52a0f56ea5cbf526ba63de401fb0aa10867a3cf87a138cfa5",
        "V4__allow_null_schedule_content_in_public.sql", "c8c8fce7632110f4ee6a09290d058a213d3afbe5c0d06bd8c2c5c2223821dfa7");
    static final Map<String, Integer> CHECKSUMS = Map.of("1",1122476101,"2",463837306,"3",-676549352,"4",1630550406);
    static String stage = "startup";
    static class GuardFailure extends RuntimeException {}
    static void require(boolean ok, String message) { if (!ok) throw new GuardFailure(); }
    static void runtimeVersion() {
        require("12.4.0".equals(VersionPrinter.getVersion()), "Unexpected Flyway version");
        System.out.println("Flyway version=12.4.0 verified before DB connection");
    }
    static String errorKind(Throwable e) {
        if (e instanceof GuardFailure) return "guard-rejected";
        if (e instanceof SQLException) return "database-error";
        if (e instanceof LinkageError) return "dependency-error";
        if (e instanceof java.io.IOException) return "io-error";
        return "runtime-error";
    }
    static String scalar(Connection c, String sql) throws SQLException {
        try (var s=c.createStatement(); var r=s.executeQuery(sql)) {
            require(r.next(), "Missing query result"); return r.getString(1);
        }
    }
    static class GuardedSource implements DataSource {
        final boolean readOnly;
        GuardedSource(boolean readOnly) { this.readOnly=readOnly; }
        public Connection getConnection() throws SQLException {
            String password=System.getenv("TRAINING_PORTAL_DB_PASSWORD");
            require(password!=null && !password.isBlank(), "DB password environment variable is missing");
            Properties p=new Properties(); p.setProperty("user","training_portal_user"); p.setProperty("password",password);
            p.setProperty("connectTimeout","5"); p.setProperty("readOnly",Boolean.toString(readOnly)); p.setProperty("readOnlyMode","always");
            Connection c=DriverManager.getConnection("jdbc:postgresql://localhost:5432/"+DB+"?currentSchema="+SCHEMA,p);
            try {
                c.setReadOnly(readOnly);
                try (var s=c.createStatement()) { s.execute("SET lock_timeout='5s'"); s.execute("SET statement_timeout='30s'"); }
                require(DB.equals(scalar(c,"select current_database()")),"Wrong database");
                require(SCHEMA.equals(scalar(c,"select current_schema()")),"Wrong schema");
                require("5s".equals(scalar(c,"show lock_timeout")),"Wrong lock timeout");
                require("30s".equals(scalar(c,"show statement_timeout")),"Wrong statement timeout");
                if (readOnly) require("on".equals(scalar(c,"show transaction_read_only")),"Read-only guard failed");
                return c;
            } catch (RuntimeException | SQLException e) { c.close(); throw e; }
        }
        public Connection getConnection(String u,String p) { throw new UnsupportedOperationException(); }
        public PrintWriter getLogWriter(){return null;} public void setLogWriter(PrintWriter p){}
        public int getLoginTimeout(){return 5;} public void setLoginTimeout(int s){}
        public Logger getParentLogger(){return Logger.getLogger("public-v4-runner");}
        public <T>T unwrap(Class<T> t)throws SQLException{throw new SQLException("Unsupported unwrap");}
        public boolean isWrapperFor(Class<?> t){return false;}
    }
    static void files(Path location) throws Exception {
        try (var walk=Files.walk(location)) {
            var paths=walk.filter(Files::isRegularFile).toList();
            require(paths.size()==4,"Unexpected migration files or callbacks");
            for (var p:paths) {
                require(p.getParent().equals(location),"Nested migration location");
                String expected=HASHES.get(p.getFileName().toString());
                require(expected!=null,"Unexpected migration file");
                require(expected.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)))),"SQL hash mismatch");
            }
        }
    }
    static void preflight(DataSource ds, boolean afterSkip) throws Exception {
        try (var c=ds.getConnection()) {
            List<String> versions=new ArrayList<>();
            try(var s=c.createStatement();var r=s.executeQuery("select version,checksum,success,script from public.flyway_schema_history order by installed_rank")) {
                while(r.next()) {
                    String v=r.getString(1); versions.add(v);
                    String script=r.getString(4);
                    require(HASHES.keySet().stream().anyMatch(name -> name.startsWith("V"+v+"__") && name.equals(script)),"Unexpected history script");
                    require(CHECKSUMS.containsKey(v)&&r.getInt(2)==CHECKSUMS.get(v)&&r.getBoolean(3),"Unexpected history or checksum");
                }
            }
            require(versions.equals(afterSkip?List.of("1","2","3"):List.of("1","2")),"Unexpected applied versions for phase");
            require("ok".equals(scalar(c,"select case when a.atttypid='pg_catalog.varchar'::regtype and a.atttypmod=54 and a.attnotnull then 'ok' else 'bad' end from pg_attribute a where a.attrelid='public.schedule_details'::regclass and a.attname='schedule_content' and not a.attisdropped")),"Expected VARCHAR(50) NOT NULL");
            require("0".equals(scalar(c,"select count(*) from pg_stat_activity where datname=current_database() and backend_type='client backend' and pid<>pg_backend_pid()")),"Other DB sessions exist");
            System.out.println("history="+versions+" column=VARCHAR(50)/NOT_NULL other_sessions=0");
        }
    }
    static Flyway phase(DataSource ds,Path location,String target,boolean skip) {
        var config=Flyway.configure().dataSource(ds).defaultSchema(SCHEMA).schemas(SCHEMA).createSchemas(false)
            .locations("filesystem:"+location).target(target).skipExecutingMigrations(skip)
            .validateOnMigrate(true).baselineOnMigrate(false).outOfOrder(false).cleanDisabled(true);
        require(config.isSkipExecutingMigrations()==skip && config.getTarget().toString().equals(target),"Wrong phase configuration");
        System.out.println("phase target="+target+" skip="+skip+" lock_timeout=5s statement_timeout=30s");
        return config.load();
    }
    static List<String> pending(Flyway f) {
        var list=Arrays.stream(f.info().pending()).map(m->{require(m.getVersion()!=null,"Unexpected repeatable migration");return m.getVersion().toString();}).toList();
        System.out.println("pending="+list);return list;
    }
    public static void main(String[] args) {
        // Suppress all Flyway connection/error logs; output only known non-secret messages.
        try {
            stage = "logging";
            LogFactory.setLogCreator(cls -> new Log(){public void debug(String s){}public void info(String s){}public void warn(String s){}public void error(String s){}public void error(String s,Exception e){}public void notice(String s){} });
            stage = "flyway-version";
            runtimeVersion();
            stage = "arguments";
            require(args.length==2,"Usage: dry-run|skip-v3|apply-v4 migration-directory");
            String mode=args[0]; require(Set.of("dry-run","skip-v3","apply-v4").contains(mode),"Unknown mode");
            boolean dry=mode.equals("dry-run"), afterSkip=mode.equals("apply-v4");
            Path location=Path.of(args[1]).toAbsolutePath().normalize();
            stage = "sql-files"; files(location);
            var ds=new GuardedSource(dry);
            stage = "preflight"; preflight(ds,afterSkip);
            stage = "pending-check";
            if(dry) {
                require(pending(phase(ds,location,"3",true)).equals(List.of("3")),"V3 is not the sole target");
                require(pending(phase(ds,location,"4",false)).equals(List.of("3","4")),"Unexpected V4 phase targets before V3 registration");
                System.out.println("dry-run PASS: read-only; V4 becomes sole pending version only AFTER V3 registration; no migrate invoked");
            } else {
                String target=afterSkip?"4":"3"; var f=phase(ds,location,target,!afterSkip);
                require(pending(f).equals(List.of(target)),"Unexpected pending migrations");
                stage = "preflight";
                preflight(ds,afterSkip); // Fresh check immediately before authorized mutation.
                stage = "sql-files"; files(location);
                stage = "flyway-version"; runtimeVersion();
                stage = "migrate";
                var result=f.migrate();
                require(result.success && result.migrationsExecuted==1 && "12.4.0".equals(result.flywayVersion),"Unexpected migration result");
                stage = "validate"; f.validate();
                System.out.println("phase success: migrated=1 validate=PASS");
            }
        } catch (Exception | LinkageError e) {
            // Fixed stage/category only, including direct Java invocation. Never print details.
            System.err.println("STOP stage="+stage+" kind="+errorKind(e));
            System.exit(1);
        }
    }
}

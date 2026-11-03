package com.training.portal.setup;

import java.nio.file.Path;
import java.sql.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Opt-in only: fixtures must be created in a disposable, independent local cluster. */
@EnabledIfEnvironmentVariable(named="FRESH_SETUP_TEST_PORT",matches="[0-9]+")
class FreshDatabaseCommandIntegrationTest {
    static int port;static String password;
    @BeforeAll static void setup(){port=Integer.parseInt(System.getenv("FRESH_SETUP_TEST_PORT"));assertThat(port).isBetween(1024,65535).isNotEqualTo(5432);password=System.getenv("FRESH_SETUP_TEST_PASSWORD");assertThat(password).isNotBlank();FreshDatabaseCommand.quietLogs();}
    FreshDatabaseCommand.Target target(String db,String schema){return new FreshDatabaseCommand.Target(port,db,schema,Path.of("src/main/resources/db/migration"));}
    Connection connection(String db,String schema) throws Exception{return DriverManager.getConnection(target(db,schema).url(),"training_portal_user",password);}
    @Test void publicPhasesAndSecondRunRejected() throws Exception {
        var t=target("fresh_public","public");FreshDatabaseCommand.execute(t,password.toCharArray());
        try(var c=connection(t.database(),t.schema())) {FreshDatabaseCommand.history(c,5);FreshDatabaseCommand.structure(c);FreshDatabaseCommand.nullable(c,true);}
        assertThatThrownBy(()->FreshDatabaseCommand.execute(t,password.toCharArray())).isInstanceOf(Exception.class);
        try(var c=connection(t.database(),t.schema())){FreshDatabaseCommand.history(c,5);}
    }
    @Test void test1UsesNormalV3AndOtherSchemaStaysEmpty() throws Exception {
        var t=target("fresh_test1","test1");FreshDatabaseCommand.execute(t,password.toCharArray());
        try(var c=connection(t.database(),t.schema())){FreshDatabaseCommand.history(c,5);FreshDatabaseCommand.structure(c);FreshDatabaseCommand.nullable(c,true);
            assertThat(FreshDatabaseCommand.scalar(c,"select count(*) from pg_tables where schemaname='public'")).isEqualTo("0");}
    }
    @Test void existingTableInOtherSchemaIsRejectedAndUnchanged() throws Exception {
        assertThatThrownBy(()->FreshDatabaseCommand.execute(target("fresh_existing","test1"),password.toCharArray())).isInstanceOf(Exception.class);
        try(var c=connection("fresh_existing","test1")){assertThat(FreshDatabaseCommand.scalar(c,"select count(*) from public.protected_fixture")).isEqualTo("1");assertThat(FreshDatabaseCommand.scalar(c,"select count(*) from pg_tables where schemaname='test1'")).isEqualTo("0");}
    }
    @Test void evenEmptyExistingHistoryIsRejected() throws Exception {
        assertThatThrownBy(()->FreshDatabaseCommand.execute(target("fresh_history","public"),password.toCharArray())).isInstanceOf(Exception.class);
        try(var c=connection("fresh_history","public")){assertThat(FreshDatabaseCommand.scalar(c,"select count(*) from flyway_schema_history")).isEqualTo("0");}
    }
    @Test void anotherClientBlocksBootstrap() throws Exception {
        try(var other=connection("fresh_busy","public")) {
            assertThatThrownBy(()->FreshDatabaseCommand.execute(target("fresh_busy","public"),password.toCharArray())).isInstanceOf(Exception.class);
            assertThat(FreshDatabaseCommand.scalar(other,"select count(*) from pg_tables where schemaname='public'")).isEqualTo("0");
        }
    }
}

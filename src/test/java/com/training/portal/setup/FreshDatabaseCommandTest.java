package com.training.portal.setup;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class FreshDatabaseCommandTest {
    String[] args(String host,String port,String db,String schema){return new String[]{"--initialize-empty-database","--host="+host,"--port="+port,"--database="+db,"--schema="+schema,"--user=training_portal_user","--migrations=src/main/resources/db/migration"};}
    @Test void explicitTargetRequired(){assertThatThrownBy(()->FreshDatabaseCommand.parse(new String[]{})).isInstanceOf(Exception.class);}
    @Test void developmentPortAndRemoteTargetsRejected(){
        for(String[] a:new String[][]{args("127.0.0.1","5432","training_portal","public"),args("localhost","55432","training_portal","public"),args("remote","55432","training_portal","public"),args("127.0.0.1","55432","postgres","public"),args("127.0.0.1","55432","bad?","public"),args("127.0.0.1","55432","training_portal","other")})
            assertThatThrownBy(()->FreshDatabaseCommand.parse(a)).isInstanceOf(Exception.class);
    }
    @Test void validExplicitTarget(){assertThat(FreshDatabaseCommand.parse(args("127.0.0.1","55432","training_portal","test1")).description()).isEqualTo("training_portal/test1@127.0.0.1:55432");}
    @Test void noConsoleRejectedBeforeDatabaseAccess(){assertThat(FreshDatabaseCommand.run(args("127.0.0.1","55432","training_portal","public"),null)).isEqualTo(1);}
    @Test void hashesMatchAndTemporaryCopyRemoved() throws Exception {Path copy=FreshDatabaseCommand.freeze(Path.of("src/main/resources/db/migration"));try(var files=Files.list(copy)){assertThat(files.count()).isEqualTo(5);}FreshDatabaseCommand.cleanup(copy);assertThat(copy).doesNotExist();}
    @Test void extraOrChangedSqlRejectedBeforeConnection(@TempDir Path dir) throws Exception {
        for(String name:FreshDatabaseCommand.HASHES.keySet())Files.copy(Path.of("src/main/resources/db/migration",name),dir.resolve(name));
        Files.writeString(dir.resolve("extra.sql"),"select 1");assertThatThrownBy(()->FreshDatabaseCommand.freeze(dir)).isInstanceOf(Exception.class);Files.delete(dir.resolve("extra.sql"));
        Files.writeString(dir.resolve("V5__add_member_auth_version.sql"),"select 1");assertThatThrownBy(()->FreshDatabaseCommand.freeze(dir)).isInstanceOf(Exception.class);
    }
}

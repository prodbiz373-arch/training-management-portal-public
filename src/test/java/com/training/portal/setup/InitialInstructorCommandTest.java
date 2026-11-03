package com.training.portal.setup;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class InitialInstructorCommandTest {
    @Test void explicitLocalTargetIsRequired() {
        var target=InitialInstructorCommand.parse(new String[]{"--initialize-first-instructor","--host=127.0.0.1","--port=59475","--database=training_portal","--schema=public"});
        assertThat(target.description()).isEqualTo("training_portal/public@127.0.0.1:59475");
    }
    @Test void unexpectedTargetsAndArgumentsAreRejected() {
        for(String[] args:new String[][]{ {},
            {"--initialize-first-instructor","--host=remote","--port=5432","--database=training_portal","--schema=public"},
            {"--initialize-first-instructor","--host=127.0.0.1","--port=5432","--database=other","--schema=public"},
            {"--initialize-first-instructor","--host=127.0.0.1","--port=5432","--database=training_portal","--schema=public;drop"}})
            assertThatThrownBy(()->InitialInstructorCommand.parse(args)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void noConsoleFailsBeforeAnyConnection() {
        assertThat(InitialInstructorCommand.run(new String[]{"--initialize-first-instructor","--host=127.0.0.1","--port=59475","--database=training_portal","--schema=public"},null)).isEqualTo(1);
    }
}

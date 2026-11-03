package com.training.portal.member;
import org.junit.jupiter.api.Test;
import com.training.portal.member.dto.*;
import static org.assertj.core.api.Assertions.*;
class PasswordCompatibilityTest {
 @Test void bcryptUtf8BoundariesAndBlankUpdateSemanticsRemainUnchanged(){var v=new BCryptInputValidator();assertThat(v.isValid("a".repeat(72),null)).isTrue();assertThat(v.isValid("a".repeat(73),null)).isFalse();assertThat(v.isValid("あ".repeat(24),null)).isTrue();assertThat(v.isValid("あ".repeat(25),null)).isFalse();assertThat(v.isValid("😀".repeat(18),null)).isTrue();assertThat(v.isValid("😀".repeat(19),null)).isFalse();assertThat(v.isValid("",null)).isTrue();assertThat(v.isValid(null,null)).isTrue();}
 @Test void unapprovedMinimumIsNotIntroduced(){try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){var validator=factory.getValidator();assertThat(validator.validate(new MemberCreateRequest("T1",(short)2,"Synthetic",null,"a"))).isEmpty();assertThat(validator.validate(new MemberCreateRequest("T1",(short)2,"Synthetic",null," "))).isNotEmpty();}}
 @Test void existingShortPasswordStillAuthenticates(){var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();var hash=encoder.encode("a");assertThat(encoder.matches("a",hash)).isTrue();assertThat(encoder.matches("b",hash)).isFalse();}
}

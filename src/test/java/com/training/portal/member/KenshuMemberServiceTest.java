package com.training.portal.member;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import com.training.portal.member.dto.*;
import com.training.portal.member.exception.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KenshuMemberServiceTest {
    static ValidatorFactory factory;
    KenshuMemberRepository repository;
    KenshuMemberService service;
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    KenshuMember member;

    @BeforeAll
    static void initializeValidator() { factory = Validation.buildDefaultValidatorFactory(); }

    @AfterAll
    static void closeValidator() { factory.close(); }

    @BeforeEach
    void setUp() {
        repository = mock(KenshuMemberRepository.class);
        service = new KenshuMemberService(repository, encoder, factory.getValidator(), mock(MemberRoleChangeSupport.class));
        member = new KenshuMember("001", (short) 1, "テスト", null,
                encoder.encode("old-password"), "MMB01", "admin");
        ReflectionTestUtils.setField(member, "updateCount", 0);
    }

    MemberCreateRequest createRequest(String password) {
        return new MemberCreateRequest("001", (short) 1, "テスト", null, password);
    }

    MemberUpdateRequest updateRequest(String password, int version) {
        return new MemberUpdateRequest((short) 2, "変更後", "かな", password, version);
    }

    @Test
    void createHashesPasswordAndSetsAuditFields() {
        when(repository.saveAndFlush(any())).thenAnswer(call -> {
            KenshuMember saved = call.getArgument(0);
            assertThat(saved.getPassword()).isNotEqualTo("secret");
            assertThat(encoder.matches("secret", saved.getPassword())).isTrue();
            assertThat(saved.getScreenId()).isEqualTo("MMB01");
            assertThat(saved.getCreateUserCd()).isEqualTo("admin");
            assertThat(saved.getUpdateUserCd()).isEqualTo("admin");
            ReflectionTestUtils.setField(saved, "updateCount", 0);
            return saved;
        });
        assertThat(service.create(createRequest("secret"), "admin").updateCount()).isZero();
    }

    @Test
    void responseSerializationHasOnlyPublicFieldsAndToStringHidesPasswords() {
        var response = MemberResponse.from(member);
        var tree = new JsonMapper().readTree(new JsonMapper().writeValueAsString(response));
        assertThat(tree.propertyNames()).containsExactlyInAnyOrder(
                "syainId", "syainKbn", "name", "nameKana", "updateCount");
        assertThat(response.toString()).doesNotContain(member.getPassword());
        assertThat(member.toString()).doesNotContain(member.getPassword());
        assertThat(createRequest("secret").toString()).doesNotContain("secret");
        assertThat(updateRequest("secret", 0).toString()).doesNotContain("secret");
    }

    @Test
    void duplicateIdIsRejected() {
        when(repository.existsById("001")).thenReturn(true);
        assertThatThrownBy(() -> service.create(createRequest("secret"), "admin"))
                .isInstanceOf(DuplicateMemberException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateIsRejected() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "constraint", new SQLException("constraint", "23505")));
        assertThatThrownBy(() -> service.create(createRequest("secret"), "admin"))
                .isInstanceOf(DuplicateMemberException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void blankUpdatePasswordPreservesHash(String password) {
        String original = member.getPassword();
        when(repository.findById("001")).thenReturn(Optional.of(member));
        service.update("001", updateRequest(password, 0), "editor");
        assertThat(member.getPassword()).isEqualTo(original);
        assertThat(member.getName()).isEqualTo("変更後");
        assertThat(member.getUpdateUserCd()).isEqualTo("editor");
        assertThat(member.getCreateUserCd()).isEqualTo("admin");
    }

    @Test
    void enteredUpdatePasswordIsHashedAndFlushedVersionIsReturned() {
        when(repository.findById("001")).thenReturn(Optional.of(member));
        doAnswer(call -> { ReflectionTestUtils.setField(member, "updateCount", 1); return null; })
                .when(repository).flush();
        var result = service.update("001", updateRequest("new-password", 0), "editor");
        assertThat(encoder.matches("new-password", member.getPassword())).isTrue();
        assertThat(encoder.matches("old-password", member.getPassword())).isFalse();
        assertThat(result.updateCount()).isEqualTo(1);
    }

    @Test
    void staleVersionIsRejectedBeforeMutation() {
        when(repository.findById("001")).thenReturn(Optional.of(member));
        assertThatThrownBy(() -> service.update("001", updateRequest(null, 1), "editor"))
                .isInstanceOf(MemberUpdateConflictException.class);
        assertThat(member.getName()).isEqualTo("テスト");
        verify(repository, never()).flush();
    }

    @Test
    void concurrentUpdateIsRejectedAtFlush() {
        when(repository.findById("001")).thenReturn(Optional.of(member));
        doThrow(new OptimisticLockingFailureException("conflict")).when(repository).flush();
        assertThatThrownBy(() -> service.update("001", updateRequest(null, 0), "editor"))
                .isInstanceOf(MemberUpdateConflictException.class);
    }

    @Test
    void missingMemberReadFails() {
        assertThatThrownBy(() -> service.findById("missing")).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void missingMemberUpdateFails() {
        assertThatThrownBy(() -> service.update("missing", updateRequest(null, 0), "editor"))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void missingMemberDeleteFails() {
        assertThatThrownBy(() -> service.delete("missing")).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void listUsesAscendingRepositoryMethod() {
        when(repository.findAllByOrderBySyainIdAsc()).thenReturn(List.of(member));
        assertThat(service.findAll()).containsExactly(MemberResponse.from(member));
        verify(repository).findAllByOrderBySyainIdAsc();
        verify(repository, never()).findAll();
    }

    @Test
    void getReturnsDto() {
        when(repository.findById("001")).thenReturn(Optional.of(member));
        assertThat(service.findById("001")).isEqualTo(MemberResponse.from(member));
    }

    @Test
    void deleteFlushes() {
        when(repository.findById("001")).thenReturn(Optional.of(member));
        service.delete("001");
        var order = inOrder(repository);
        order.verify(repository).delete(member);
        order.verify(repository).flush();
    }

    @Test
    void referencedDeleteIsBusinessException() {
        when(repository.findById("001")).thenReturn(Optional.of(member));
        doThrow(new DataIntegrityViolationException("constraint", new SQLException("fk", "23503")))
                .when(repository).flush();
        assertThatThrownBy(() -> service.delete("001")).isInstanceOf(MemberReferencedException.class);
    }

    @Test
    void unrelatedIntegrityErrorIsPropagated() {
        when(repository.findById("001")).thenReturn(Optional.of(member));
        var failure = new DataIntegrityViolationException("other error");
        doThrow(failure).when(repository).flush();
        assertThatThrownBy(() -> service.delete("001")).isSameAs(failure);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void createRequiresPassword(String password) {
        assertThatThrownBy(() -> service.create(createRequest(password), "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void bcryptByteLimitIsValidatedWithoutLeakingInput() {
        for (String password : List.of("a".repeat(73), "あ".repeat(25), "😀".repeat(19))) {
            assertThatThrownBy(() -> service.create(createRequest(password), "admin"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(password);
            assertThatThrownBy(() -> service.update("001", updateRequest(password, 0), "admin"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(password);
        }
        verifyNoInteractions(repository);
        for (String password : List.of("a", "a".repeat(72), "あ".repeat(24), "😀".repeat(18))) {
            assertThat(factory.getValidator().validate(createRequest(password))).isEmpty();
            assertThat(encoder.matches(password, encoder.encode(password))).isTrue();
        }
    }

    @Test
    void validatesTableBoundaries() {
        var validator = factory.getValidator();
        assertThat(validator.validate(new MemberCreateRequest("a".repeat(10), (short) 2,
                "名".repeat(50), "か".repeat(100), "x"))).isEmpty();
        Stream.of(
                new MemberCreateRequest(null, (short) 1, "name", null, "x"),
                new MemberCreateRequest(" ", (short) 1, "name", null, "x"),
                new MemberCreateRequest("a".repeat(11), (short) 1, "name", null, "x"),
                new MemberCreateRequest("001", null, "name", null, "x"),
                new MemberCreateRequest("001", (short) 0, "name", null, "x"),
                new MemberCreateRequest("001", (short) 3, "name", null, "x"),
                new MemberCreateRequest("001", (short) 1, " ", null, "x"),
                new MemberCreateRequest("001", (short) 1, "a".repeat(51), null, "x"),
                new MemberCreateRequest("001", (short) 1, "name", "a".repeat(101), "x"),
                new MemberUpdateRequest((short) 1, "name", null, null, null),
                updateRequest(null, -1))
                .forEach(request -> assertThat(validator.validate(request)).isNotEmpty());
    }
}

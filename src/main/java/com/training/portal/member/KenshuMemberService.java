package com.training.portal.member;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import jakarta.validation.Validator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.training.portal.member.dto.*;
import com.training.portal.member.exception.*;

@Service
@Transactional(readOnly = true)
public class KenshuMemberService {
    private static final String SCREEN_ID = "MMB01";
    private final KenshuMemberRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final MemberRoleChangeSupport roleChanges;

    public KenshuMemberService(KenshuMemberRepository repository,
            PasswordEncoder passwordEncoder, Validator validator, MemberRoleChangeSupport roleChanges) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
        this.roleChanges = roleChanges;
    }

    public List<MemberResponse> findAll() {
        return repository.findAllByOrderBySyainIdAsc().stream().map(MemberResponse::from).toList();
    }

    public MemberResponse findById(String syainId) {
        return MemberResponse.from(requireMember(syainId));
    }

    @Transactional
    public MemberResponse create(MemberCreateRequest request, String operationSyainId) {
        validate(request);
        validateOperator(operationSyainId);
        if (repository.existsById(request.syainId())) {
            throw new DuplicateMemberException();
        }
        var member = new KenshuMember(request.syainId(), request.syainKbn(), request.name(),
                request.nameKana(), passwordEncoder.encode(request.password()), SCREEN_ID, operationSyainId);
        try {
            return MemberResponse.from(repository.saveAndFlush(member));
        } catch (DataIntegrityViolationException ex) {
            // 事前確認後の同時登録も、DBの一意制約で検出する。
            if (hasSqlState(ex, "23505")) {
                throw new DuplicateMemberException();
            }
            throw ex;
        }
    }

    @Transactional
    public MemberResponse update(String syainId, MemberUpdateRequest request, String operationSyainId) {
        validate(request);
        validateOperator(operationSyainId);
        var member = requireMember(syainId);
        if (!Objects.equals(request.updateCount(), member.getUpdateCount())) {
            throw new MemberUpdateConflictException();
        }
        if (!member.getSyainKbn().equals(request.syainKbn())) {
            roleChanges.change(syainId, request.syainKbn(), operationSyainId);
        }
        member.updateProfile(request.syainKbn(), request.name(), request.nameKana(), SCREEN_ID, operationSyainId);
        if (request.password() != null && !request.password().isBlank()) {
            member.changePassword(passwordEncoder.encode(request.password()), SCREEN_ID, operationSyainId);
        }
        try {
            // DTOへ変換する前に@Versionの増分と実際の競合を確定する。
            repository.flush();
        } catch (OptimisticLockingFailureException ex) {
            throw new MemberUpdateConflictException();
        }
        return MemberResponse.from(member);
    }

    @Transactional
    public void delete(String syainId) {
        var member = requireMember(syainId);
        try {
            repository.delete(member);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (hasSqlState(ex, "23503")) {
                throw new MemberReferencedException();
            }
            throw ex;
        } catch (OptimisticLockingFailureException ex) {
            throw new MemberUpdateConflictException();
        }
    }

    private KenshuMember requireMember(String syainId) {
        return repository.findById(syainId).orElseThrow(MemberNotFoundException::new);
    }

    private void validate(Object request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            // ConstraintViolationのinvalidValueやDTOを例外メッセージへ含めない。
            throw new IllegalArgumentException("メンバーの入力内容が不正です");
        }
    }

    private void validateOperator(String operationSyainId) {
        if (operationSyainId == null || operationSyainId.isBlank() || operationSyainId.length() > 10) {
            throw new IllegalArgumentException("操作社員IDが不正です");
        }
    }

    private boolean hasSqlState(Throwable error, String state) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && state.equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}

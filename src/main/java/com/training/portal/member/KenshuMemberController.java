package com.training.portal.member;

import java.util.List;
import org.springframework.security.core.Authentication;
import com.training.portal.member.exception.MemberSelfDeleteNotAllowedException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import com.training.portal.member.dto.*;

@RestController
@RequestMapping("/api/members")
public class KenshuMemberController {
    private final KenshuMemberService service;

    public KenshuMemberController(KenshuMemberService service) {
        this.service = service;
    }

    @GetMapping
    public List<MemberResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{syainId}")
    public MemberResponse findById(@PathVariable @NotBlank @Size(max = 10) String syainId) {
        return service.findById(syainId);
    }

    @PostMapping
    public ResponseEntity<MemberResponse> create(@Valid @RequestBody MemberCreateRequest request,
            Authentication authentication) {
        var response = service.create(request, authentication.getName());
        var location = UriComponentsBuilder.fromPath("/api/members/{syainId}")
                .encode().buildAndExpand(response.syainId()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{syainId}")
    public MemberResponse update(@PathVariable @NotBlank @Size(max = 10) String syainId,
            @Valid @RequestBody MemberUpdateRequest request,
            Authentication authentication) {
        return service.update(syainId, request, authentication.getName());
    }

    @DeleteMapping("/{syainId}")
    public ResponseEntity<Void> delete(@PathVariable @NotBlank @Size(max = 10) String syainId, Authentication authentication) {
        if (syainId.equals(authentication.getName())) {
            throw new MemberSelfDeleteNotAllowedException();
        }
        service.delete(syainId);
        return ResponseEntity.noContent().build();
    }
}

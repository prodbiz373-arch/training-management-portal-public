package com.training.portal.audit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AuditFailureLoginTest {
 @Test void loginStillCreatesSessionWhenAuditSinkThrows()throws Exception{
  var manager=mock(org.springframework.security.authentication.AuthenticationManager.class);
  var strategy=mock(org.springframework.security.web.authentication.session.SessionAuthenticationStrategy.class);
  var contexts=new org.springframework.security.web.context.HttpSessionSecurityContextRepository();
  var members=mock(com.training.portal.member.KenshuMemberService.class);
  var principal=new com.training.portal.auth.MemberPrincipal("A001","unused",(short)2,0);
  when(manager.authenticate(any())).thenReturn(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
  when(members.findById("A001")).thenReturn(new com.training.portal.member.dto.MemberResponse("A001",(short)2,"Synthetic",null,0));
  var limiter=new com.training.portal.auth.LoginAttemptLimiter(8,200,300,60,60,100,32);
  try(var audit=new SecurityAudit(new byte[32],4,event->{throw new java.io.IOException("synthetic disk full");})){
   var controller=new com.training.portal.auth.AuthController(manager,strategy,contexts,members,limiter,audit);
   var request=new org.springframework.mock.web.MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");
   var result=controller.login(new com.training.portal.auth.LoginRequest("A001","synthetic-secret"),request,new org.springframework.mock.web.MockHttpServletResponse());
   assertThat(result.syainId()).isEqualTo("A001");assertThat(request.getSession(false)).isNotNull();verify(strategy).onAuthentication(any(),eq(request),any());
  }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 }
}

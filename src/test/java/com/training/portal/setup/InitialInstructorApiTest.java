package com.training.portal.setup;
import com.training.portal.member.*;
import com.training.portal.member.dto.MemberCreateRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Import(InitialInstructorService.class)
@Transactional
class InitialInstructorApiTest {
 @Autowired InitialInstructorService setup;
 @Autowired KenshuMemberRepository repository;
 @Autowired KenshuMemberService members;
 @Autowired PasswordEncoder encoder;
 @Autowired JdbcTemplate jdbc;
 @Autowired MockMvc mvc;
 static final String SECRET="synthetic-test-only";
 MemberCreateRequest input(String id){return new MemberCreateRequest(id,(short)2,"合成初期講師",null,SECRET);}
 @BeforeEach void guard(){assertThat(jdbc.queryForObject("select current_schema()",String.class)).isEqualTo("test1");assertThat(repository.count()).isZero();}
 @Test void initialInstructorLogsInAndCreatesBothRolesThroughExistingApi() throws Exception {
  setup.create(input("FIRST"));var member=repository.findById("FIRST").orElseThrow();
  assertThat(member.getSyainKbn()).isEqualTo((short)2);assertThat(member.getAuthVersion()).isZero();
  assertThat(member.getPassword()).startsWith("$2a$10$");assertThat(encoder.matches(SECRET,member.getPassword())).isTrue();
  var login=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
   .content("{\"syainId\":\"FIRST\",\"password\":\""+SECRET+"\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.syainKbn").value(2)).andReturn();
  var session=(MockHttpSession)login.getRequest().getSession(false);
  mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
  mvc.perform(post("/api/members").session(session).contentType("application/json")
   .content("{\"syainId\":\"BLOCKED\",\"syainKbn\":2,\"name\":\"合成\",\"password\":\""+SECRET+"\"}"))
   .andExpect(status().isForbidden());
  assertThat(repository.count()).isEqualTo(1);
  for(int role:new int[]{1,2})mvc.perform(post("/api/members").session(session).with(csrf()).contentType("application/json")
   .content("{\"syainId\":\"NEXT"+role+"\",\"syainKbn\":"+role+",\"name\":\"合成社員\",\"password\":\""+SECRET+"\"}"))
   .andExpect(status().isCreated());
  assertThat(repository.count()).isEqualTo(3);
 }
 @Test void repeatedSetupDoesNotAddAnotherInstructor(){setup.create(input("FIRST"));assertThatThrownBy(()->setup.create(input("SECOND"))).isInstanceOf(IllegalStateException.class);assertThat(repository.count()).isEqualTo(1);}
 @Test void existingTraineeAlsoPreventsBootstrap() {
  members.create(new MemberCreateRequest("EXISTING",(short)1,"既存合成新人",null,SECRET),"EXISTING");
  assertThatThrownBy(()->setup.create(input("FIRST"))).isInstanceOf(IllegalStateException.class);
  assertThat(repository.count()).isEqualTo(1);assertThat(repository.findById("EXISTING").orElseThrow().getSyainKbn()).isEqualTo((short)1);
 }
 @ParameterizedTest @ValueSource(strings={"id","name","password","bytes","role"})
 void invalidInputDoesNotCreateMember(String kind){
  var request=new MemberCreateRequest(kind.equals("id")?" ":"FIRST",(short)(kind.equals("role")?1:2),kind.equals("name")?"x".repeat(51):"合成",null,kind.equals("password")?" ":kind.equals("bytes")?"x".repeat(73):SECRET);
  assertThatThrownBy(()->setup.create(request)).isInstanceOf(IllegalArgumentException.class);assertThat(repository.count()).isZero();
 }
 @Test void anonymousCannotCreateInstructorEvenWithCsrf() throws Exception {
  mvc.perform(post("/api/members").with(csrf()).contentType("application/json").content("{\"syainId\":\"FIRST\",\"syainKbn\":2,\"name\":\"合成\",\"password\":\""+SECRET+"\"}"))
   .andExpect(status().isUnauthorized());assertThat(repository.count()).isZero();
 }
}

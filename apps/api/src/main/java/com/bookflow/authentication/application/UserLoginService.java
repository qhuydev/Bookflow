package com.bookflow.authentication.application;

import com.bookflow.authentication.config.AuthenticationProperties;
import com.bookflow.authentication.domain.*;
import com.bookflow.authentication.password.Argon2idPasswordHasher;
import com.bookflow.authentication.repository.UserAuthenticationRepository;
import com.bookflow.authentication.token.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service @Profile("!test")
public class UserLoginService {
 private static final String DUMMY_HASH="$argon2id$v=19$m=19456,t=2,p=1$AAECAwQFBgcICQoLDA0ODw$8lfYaMMYyVtwpJzG8lvs9l+Umt5Cd1dd8jMuw0oFeo8";
 private final LoginRequestValidator validator; private final UserAuthenticationRepository repository; private final Argon2idPasswordHasher passwords; private final RefreshTokenGenerator refreshes; private final AccessTokenIssuer tokens; private final AuthenticationProperties props;
 public UserLoginService(LoginRequestValidator v,UserAuthenticationRepository r,Argon2idPasswordHasher p,RefreshTokenGenerator g,AccessTokenIssuer t,AuthenticationProperties a){validator=v;repository=r;passwords=p;refreshes=g;tokens=t;props=a;}
 @Transactional public LoginResult login(String email,String password){validator.validate(email,password); LoginUser user=repository.findByNormalizedEmail(EmailNormalizer.normalize(email)).orElse(null); if(user==null){passwords.matches(password,DUMMY_HASH);throw new InvalidCredentialsException();} if(!passwords.matches(password,user.passwordHash())||user.status()!=UserStatus.ACTIVE)throw new InvalidCredentialsException(); Instant now=Instant.now(); UUID sid=UUID.randomUUID(); Instant absolute=now.plus(props.session().absoluteDays(),ChronoUnit.DAYS); Instant inactivity=now.plus(props.session().inactivityDays(),ChronoUnit.DAYS); var refresh=refreshes.generate(); repository.persistSuccessfulLogin(user.id(),now,new NewAuthenticationSession(sid,user.id(),now,inactivity,absolute),new NewRefreshToken(UUID.randomUUID(),user.id(),sid,refresh.hash(),now,inactivity,absolute)); var access=tokens.issue(user.id(),sid,now); return new LoginResult(access.token(),access.expiresInSeconds(),refresh.rawToken());}
 public record LoginResult(String accessToken,long expiresIn,String refreshToken) { }
 @Transactional(noRollbackFor = RefreshTokenReuseException.class) public LoginResult refresh(String rawToken){
  if(rawToken==null||rawToken.isBlank()) throw new RefreshTokenException(RefreshTokenException.Kind.MISSING);
  Instant now=Instant.now(); StoredRefreshToken current=repository.lockRefreshToken(refreshes.sha256(rawToken));
  if(current==null) throw new RefreshTokenException(RefreshTokenException.Kind.INVALID);
  if(!"ACTIVE".equals(current.status())) { repository.revokeFamily(current.familyId(),now,"REFRESH_REUSE"); throw new RefreshTokenReuseException(); }
  if(now.isAfter(current.inactivityExpiresAt())||now.isAfter(current.absoluteExpiresAt())) { repository.revokeFamily(current.familyId(),now,"EXPIRED"); throw new RefreshTokenException(RefreshTokenException.Kind.INVALID); }
  Instant inactivity=now.plus(props.session().inactivityDays(),ChronoUnit.DAYS); if(inactivity.isAfter(current.absoluteExpiresAt())) inactivity=current.absoluteExpiresAt();
  var generated=refreshes.generate(); repository.rotateRefreshToken(current,new com.bookflow.authentication.domain.RotatedRefreshToken(generated.rawToken(),generated.hash()),now,inactivity,current.absoluteExpiresAt()); var access=tokens.issue(current.userId(),current.familyId(),now); return new LoginResult(access.token(),access.expiresInSeconds(),generated.rawToken());
 }
 @Transactional public void logout(String rawToken){ if(rawToken==null||rawToken.isBlank()) return; StoredRefreshToken current=repository.lockRefreshToken(refreshes.sha256(rawToken)); if(current!=null) repository.revokeFamily(current.familyId(),Instant.now(),"LOGOUT"); }
 @Transactional public void logoutAll(UUID userId){ if(userId!=null) repository.revokeAllForUser(userId,Instant.now(),"LOGOUT_ALL"); }
}

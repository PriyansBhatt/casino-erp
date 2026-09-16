package com.casino.casinoerp;
import com.casino.casinoerp.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
class JwtConfigurationTests {
 @Test void missingConfigurationFailsStartup(){new ApplicationContextRunner().withUserConfiguration(JwtService.class).run(c->assertThat(c).hasFailed());}
 @ParameterizedTest @ValueSource(strings={"","   ","short","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","placeholder-AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPp0123456789012345678901234567890123456789"})
 void unsafeConfigurationRejectedWithoutEcho(String value){assertThatThrownBy(()->new JwtService(value)).isInstanceOf(IllegalStateException.class).hasMessage("JWT_SIGNING_SECRET must be an externally supplied strong random secret of at least 64 UTF-8 bytes.");}
 @Test void strongExternalKeySignsHs512(){String secret=java.util.Base64.getEncoder().encodeToString(io.jsonwebtoken.Jwts.SIG.HS512.key().build().getEncoded());var jwt=new JwtService(secret);String token=jwt.generateToken("user","CASHIER");assertThat(jwt.extractUsername(token)).isEqualTo("user");assertThat(jwt.extractRole(token)).isEqualTo("CASHIER");String header=new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[0]),java.nio.charset.StandardCharsets.UTF_8);assertThat(header).contains("HS512");}
}

package security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.annotation.FacesConfig;
import jakarta.security.enterprise.authentication.mechanism.http.CustomFormAuthenticationMechanismDefinition;
import jakarta.security.enterprise.authentication.mechanism.http.LoginToContinue;

@CustomFormAuthenticationMechanismDefinition(
         loginToContinue = @LoginToContinue(
                  loginPage = "/login.xhtml",
                  errorPage = "",
                  useForwardToLogin = false
                  )
         )

// https://rieckpil.de/howto-simple-form-based-authentication-for-jsf-2-3-with-java-ee-8-security-api/

@FacesConfig
@ApplicationScoped
public class ApplicationConfig {

}

package security;

import java.util.Arrays;
import java.util.HashSet;

import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStore;
import jakarta.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author svetozar.radojcin
 *
 */
@ApplicationScoped
public class CustomInMemoryIdentityStore implements IdentityStore {

    private static final Logger log = LoggerFactory.getLogger(CustomInMemoryIdentityStore.class);

    @Override
    public CredentialValidationResult validate(Credential credential) {
    	log.info("validate() call");
        UsernamePasswordCredential login = (UsernamePasswordCredential) credential;

        if (login.getCaller().equals("admin@mail.com")
                       && login.getPasswordAsString().equals("ADMIN123")) {
        	log.info("admin");
            return new CredentialValidationResult("admin", new HashSet<>(Arrays.asList("ADMIN", "USER")));
        } else if (login.getCaller().equals("user@mail.com")
                       && login.getPasswordAsString().equals("USER123")) {
        	log.info("user");
            return new CredentialValidationResult("user", new HashSet<>(Arrays.asList("USER")));
        }
        return CredentialValidationResult.NOT_VALIDATED_RESULT;
        // NOTE: the roles returned above are handed to the container, so
        // ExternalContext.isUserInRole(...) reflects them for the rest of the session.
        // SecurityContextAccessCheckInterceptor reads them straight from there — there is
        // deliberately no copy into a "user_roles" session attribute anymore (single source
        // of truth = the platform's caller identity).
    }
}

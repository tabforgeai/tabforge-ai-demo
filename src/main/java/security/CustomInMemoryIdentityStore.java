package security;

import java.util.Arrays;
import java.util.HashSet;

import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.context.FacesContext;

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
        HashSet<String> ulogeSet = new HashSet<String>();
        CredentialValidationResult result = null;
 
        if (login.getCaller().equals("admin@mail.com") 
                       && login.getPasswordAsString().equals("ADMIN123")) {
        	log.info("admin");
            result =  new CredentialValidationResult("admin", new HashSet<>(Arrays.asList("ADMIN", "USER")));
            ulogeSet.add("ADMIN");
            ulogeSet.add("USER"); 
        } else if (login.getCaller().equals("user@mail.com") 
                       && login.getPasswordAsString().equals("USER123")) {
        	log.info("user");
            result =  new CredentialValidationResult("user", new HashSet<>(Arrays.asList("USER")));
            ulogeSet.add("USER");
        } else {
            result =  CredentialValidationResult.NOT_VALIDATED_RESULT;
        }
        // now, because of AbstractAccessCheckInterceptor.getUserRoles(), put user roles, in the "user_roles" session atribute:
        try {
            FacesContext facesContext = FacesContext.getCurrentInstance();
            if (facesContext != null) {
                facesContext.getExternalContext().getSessionMap().put("user_roles", ulogeSet);
            }
        } catch (NoClassDefFoundError e) {
            // FacesContext not available (e.g., in unit tests without JSF runtime)
        }
        return result;
    }
}

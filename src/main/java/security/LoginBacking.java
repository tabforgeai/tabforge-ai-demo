package security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.SecurityContext;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Named
@RequestScoped
public class LoginBacking {

	private static final Logger log = LoggerFactory.getLogger(LoginBacking.class);

	private String password;

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	private String email;

	@Inject
	private SecurityContext securityContext;

	@Inject
	private ExternalContext externalContext;

	@Inject
	private FacesContext facesContext;

	public String submit() throws IOException {
		String result = "";
		log.info("login submit() begin");
		switch (continueAuthentication()) {
		case SEND_CONTINUE:
			result = "main";
			facesContext.responseComplete();
			break;
		case SEND_FAILURE:
			log.warn("failure, poruka o gresci");
			facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Login failed!",
					"Use admin@mail.com/ADMIN123 or user@mail.com/USER123"));
			break;
		case SUCCESS:
			log.info("success, ide redirect");
			result = "main";
			facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Uspešna pijava", null));
			externalContext.redirect(externalContext.getRequestContextPath() + "/main.xhtml");
			break;
		case NOT_DONE:
		}
		log.info("login submit() end");
		return result;
	}

	private AuthenticationStatus continueAuthentication() {
		return securityContext.authenticate((HttpServletRequest) externalContext.getRequest(),
				(HttpServletResponse) externalContext.getResponse(),
				AuthenticationParameters.withParams().credential(new UsernamePasswordCredential(email, password)));
	}

}
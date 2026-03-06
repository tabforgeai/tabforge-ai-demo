package security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.ExternalContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

@Named
@RequestScoped
public class LogoutBacking {

	@Inject
	private ExternalContext externalContext;

	public String submit() {
		HttpServletRequest request = (HttpServletRequest) externalContext.getRequest();
		try {
			request.logout();
		} catch (ServletException e) {
			e.printStackTrace();
		}

		externalContext.invalidateSession();
		// service.refreshEmCache();
		return "/login.xhtml?faces-redirect=true";
	}
}
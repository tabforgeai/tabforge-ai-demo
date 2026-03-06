package cdibeans;

import org.primefaces.PrimeFaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.BaseDyntabCdiBean;
import dyntabs.DynTabCDIEvent;
import dyntabs.annotation.DynTab;
import dyntabs.scope.TabScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;

@Named
@TabScoped
@DynTab(name = "HomeDynTab", uniqueIdentifier = "Home", title = "Welcome", includePage = "/WEB-INF/include/home/home.xhtml", closeable = false)
public class HomeBean extends BaseDyntabCdiBean {
	private static final Logger log = LoggerFactory.getLogger(HomeBean.class);

	@Override
	protected void onJobFlowReturn(String senderId, Object jobFlowReturnValue) {
		super.onJobFlowReturn(senderId, jobFlowReturnValue);
		log.debug("HomeBean.onJobFlowReturn(), senderId = {}, jobFlowReturnValue = {}", senderId, jobFlowReturnValue);
		FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
				this.getDynTab().getTitle() + ".onJobFlowReturn(), return value: " + jobFlowReturnValue));
		PrimeFaces.current().ajax().update("mainForm:msgs");

	}

	@Override
	protected void onDynTabAdded(DynTabCDIEvent event) {
		log.debug("HomeBean.onDynTabAdded()!");
		super.onDynTabAdded(event);
		FacesContext.getCurrentInstance().addMessage(null,
				new FacesMessage(this.getDynTab().getTitle() + ".onDynTabAdded(): " + event.getTab().getTitle()));
		PrimeFaces.current().ajax().update("mainForm:msgs");

	}

	@Override
	protected void onDynTabRemoved(DynTabCDIEvent event) {
		log.debug("HomeBean.onDynTabRemoved()!");
		super.onDynTabRemoved(event);
		FacesContext.getCurrentInstance().addMessage(null,
				new FacesMessage(this.getDynTab().getTitle() + ".onDynTabRemoved(): " + event.getTitle()));
		PrimeFaces.current().ajax().update("mainForm:msgs");

	}

	@Override
	protected void onDynTabSelected(DynTabCDIEvent event) {
		log.debug("HomeBean.onDynTabSelected()!");
		super.onDynTabSelected(event);
		FacesContext.getCurrentInstance().addMessage(null,
				new FacesMessage(this.getDynTab().getTitle() + ".onDynTabSelected(): " + event.getTab().getTitle()));
		PrimeFaces.current().ajax().update("mainForm:msgs");

	}

	@Override
	protected void onThisTabSelected() {
		log.debug("HomeBean.onThisTabSelected()!");
		super.onThisTabSelected();
		FacesContext.getCurrentInstance().addMessage(null,
				new FacesMessage(this.getDynTab().getTitle() + ".onThisTabSelected()"));
		PrimeFaces.current().ajax().update("mainForm:msgs");

	}

}

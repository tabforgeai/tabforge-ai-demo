package cdibeans;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.primefaces.PrimeFaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.BaseDyntabCdiBean;
import dyntabs.DynTabCDIEvent;
import dyntabs.DynTabManager;
import dyntabs.annotation.DynTab;
import dyntabs.scope.TabScoped;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import model.Product;
import services.ProductService;

@Named
@TabScoped
@DynTab(name = "ProizvodiDynTab", uniqueIdentifier = "Proizvodi", title = "Products", includePage = "/WEB-INF/include/proizvodi/proizvodi.xhtml", closeable = true)

@DynTab(name = "DiscountedProductsDynTab", uniqueIdentifier = "DiscountedProducts", securedResource = true, allowedRoles = {
		"ADMIN" }, title = "Discounted Products", includePage = "/WEB-INF/include/proizvodi/proizvodi.xhtml", closeable = true, parameters = {
				"discount=true" })

public class Proizvodi extends BaseDyntabCdiBean {

	private static final Logger log = LoggerFactory.getLogger(Proizvodi.class);
	private static final long serialVersionUID = 1L;

	private List<Product> products = new ArrayList<Product>();

	private Product selectedProduct;

	private List<Product> selectedProducts;

	@Inject
	private ProductService productService;

	@PostConstruct
	public void init() {
		// this.products = this.productService.getClonedProducts(100);
		// this.selectedProducts = new ArrayList<Product>();
	}

	@Override
	protected void accessPointMethod(Map parameters) {
		super.accessPointMethod(parameters);
		if (parameters != null) {
			Product prodParam = (Product) parameters.get("prodParam");
			if (prodParam != null) {
				products.add(prodParam);
			} else {// is there discount param?
				Boolean discount = (Boolean) parameters.get("discount");
				if (Boolean.TRUE.equals(discount)) {
					this.products = this.productService.getClonedProducts(15);
				} else {
					this.products = this.productService.getClonedProducts(100);
				}
			}
		}
		this.selectedProducts = new ArrayList<Product>();

	}

	@Override
	protected void exitPointMethod(Map parameters) {
		super.exitPointMethod(parameters);
	}

	public List<Product> getProducts() {
		return products;
	}

	public Product getSelectedProduct() {
		return selectedProduct;
	}

	public void setSelectedProduct(Product selectedProduct) {
		this.selectedProduct = selectedProduct;
	}

	public List<Product> getSelectedProducts() {
		return selectedProducts;
	}

	public void setSelectedProducts(List<Product> selectedProducts) {
		this.selectedProducts = selectedProducts;
	}

	public boolean hasSelectedProducts() {
		return this.selectedProducts != null && !this.selectedProducts.isEmpty();
	}

	// --------------- DybTab specific methods: ------------

	public void cloneTab() {
		String uniqueTabId = generateUniqueTabUd();
		DynTabManager.getCurrentInstance().launchDynamicTab("Proizvodi" + uniqueTabId + "DynTab",
				"Product Clone " + uniqueTabId.substring(0, 9), "/WEB-INF/include/proizvodi/proizvodi.xhtml",
				"Proizvodi" + uniqueTabId, // UniqueIdentifier
				true, cdibeans.Proizvodi.class, null); // with no params

	}

	private String generateUniqueTabUd() {
		return UUID.randomUUID().toString().replaceAll("-", "");
	}

	/**
	 * action za link u koloni "Name" na proizvodi_dataTable.xhtml Pokrece (ili
	 * selektuje, ako vec postoji) novi dynTab, sa uniqueIdentifier u obliku:
	 * "ProizvodiVIP" + prodCode
	 * 
	 * @param prodCode
	 * @param prodName
	 */
	public void launchNewTab(Product product) {
		DynTabManager.getCurrentInstance().launchDynamicTab("ProizvodiVIP" + product.getCode() + "DynTab",
				"Product VIP " + product.getName(), "/WEB-INF/include/proizvodi/proizvodi.xhtml",
				"ProizvodiVIP" + product.getCode(), // UniqueIdentifier
				true, cdibeans.Proizvodi.class, java.util.Map.of("kategorija", "vip", "prodParam", product));

	}

	/**
	 * akcija za dugme id="sendMToAppModuleBtn" na proizvodi_dataTable.xhtml, koje
	 * se vidi samo ako je parametar "kategorija" = "vip"
	 * 
	 * @param prod
	 */
	public void notifyProizvodChanges(Product prod) {
		sendMessageToAppModule("Proizvodi", prod);

	}

	/**
	 * Reakcija na application message koji salje sendMessageToAppModule() i
	 * sendMessageToAllAppModules()
	 */
	@Override
	protected void onApplicationMessage(String senderId, Object payload) {
		super.onApplicationMessage(senderId, payload);
		log.debug("onApplicationMessage(), recevier uniqueId  = {}, senderId = {}, payload = {}",
				this.getUniqueIdentifier(), senderId, payload);
		FacesContext.getCurrentInstance().addMessage(null,
				new FacesMessage(
						this.getDynTab().getTitle() + ".onApplicationMessage(), payload: " + payload.toString(),
						payload.toString()));
		PrimeFaces.current().ajax().update("mainForm:msgs");
	}

	/**
	 * reakcija na dugme id="closeAndReturnCaller" - "Close return to caller" u
	 * tabeli proizvodi_dataTable.xhtml
	 * 
	 * Dugme zatvara tekuci DybTab na kome se nalazi, i salje vrednost ka caller
	 * dynTab-u, koja ovde dolazi kao jobFlowReturnValue parametar
	 * 
	 */
	@Override
	protected void onJobFlowReturn(String senderId, Object jobFlowReturnValue) {
		super.onJobFlowReturn(senderId, jobFlowReturnValue);
		log.debug("Products.onJobFlowReturn(), senderId = {}, jobFlowReturnValue = {}", senderId, jobFlowReturnValue);
		FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
				this.getDynTab().getTitle() + ".onJobFlowReturn(), return value: " + jobFlowReturnValue));
		PrimeFaces.current().ajax().update("mainForm:msgs");

	}

	@Override
	protected void onDynTabAdded(DynTabCDIEvent event) {
		log.debug("Products.onDynTabAdded()!");
		super.onDynTabAdded(event);
		FacesContext.getCurrentInstance().addMessage(null,
				new FacesMessage(this.getDynTab().getTitle() + ".onDynTabAdded(): " + event.getTab().getTitle()));
		PrimeFaces.current().ajax().update("mainForm:msgs");

	}

	@Override
	protected void onDynTabRemoved(DynTabCDIEvent event) {
		log.debug("Proizvodi.onDynTabRemoved()!");
		super.onDynTabRemoved(event);
		FacesContext.getCurrentInstance().addMessage(null,
				new FacesMessage(this.getDynTab().getTitle() + ".onDynTabRemoved(): " + event.getTitle()));
		PrimeFaces.current().ajax().update("mainForm:msgs");

	}

	@Override
	protected void onDynTabSelected(DynTabCDIEvent event) {
		log.debug("Proizvodi.onDynTabSelected()!");
		super.onDynTabSelected(event);
		FacesContext.getCurrentInstance().addMessage(null,
				new FacesMessage(this.getDynTab().getTitle() + ".onDynTabSelected(): " + event.getTab().getTitle()));
		PrimeFaces.current().ajax().update("mainForm:msgs");

	}

	@Override
	protected void onThisTabSelected() {
		log.debug("Proizvodi.onThisTabSelected()!");
		super.onThisTabSelected();
		FacesContext.getCurrentInstance().addMessage(null,
				new FacesMessage(this.getDynTab().getTitle() + ".onThisTabSelected()"));
		PrimeFaces.current().ajax().update("mainForm:msgs");

	}

}

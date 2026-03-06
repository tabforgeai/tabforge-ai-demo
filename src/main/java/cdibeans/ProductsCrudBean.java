package cdibeans;

import java.util.List;
import java.util.UUID;

import org.primefaces.PrimeFaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.BaseDyntabCdiBean;
import dyntabs.DynTabManager;
import dyntabs.JsfUtils;
import dyntabs.annotation.DynTab;
import dyntabs.scope.TabScoped;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import model.Product;
import security.AccessCheck;
import services.ProductService;

@Named
@TabScoped
@DynTab(name = "ProductsCrudDynTab", uniqueIdentifier = "ProductsCrud", title = "Products - CRUD demo", includePage = "/WEB-INF/include/proizvodi/products_crud.xhtml", closeable = true)

public class ProductsCrudBean extends BaseDyntabCdiBean {

	private static final Logger log = LoggerFactory.getLogger(ProductsCrudBean.class);
	private static final long serialVersionUID = 1L;

	private List<Product> products;

	private Product selectedProduct;

	// to be used in CRUD operation, referenced in the dialog's UI properties:
	private Product productData;

	public Product getProductData() {
		return productData;
	}

	public void setProductData(Product productData) {
		this.productData = productData;
	}

	@Inject
	private ProductService productService;

	@PostConstruct
	public void init() {
		this.products = this.productService.getClonedProducts(100);
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

	public void openNew() {
		setProductData(new Product());
	}

	public void saveProduct() {
		if (this.productData.getCode() == null) {// create new
			this.productData.setCode(UUID.randomUUID().toString().replaceAll("-", "").substring(0, 9));
			this.products.addFirst(this.productData);
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Product Added"));
		} else {// update current
			FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Product Updated"));
		}
		// make product we worked with in dialog, as selected row in p:dataTable:
		setSelectedProduct(getProductData());
		// Close the CRUD dialog:
		// PrimeFaces.current().executeScript("PF('manageProductDialog').hide()");
		getDynTab().getDynTabVisibleMap().remove("dlg_tabB1");
		JsfUtils.refreshComponentInDynamicTab("dlg_tabB1", getDynTabId());
		// refresh p:dataTable and msgs:
		JsfUtils.refreshComponentInDynamicTab("tabB1", getDynTabId());
		PrimeFaces.current().ajax().update("mainForm:msgs");
	}

	public void deleteProduct() {
		this.products.remove(this.selectedProduct);
		this.selectedProduct = null;
		FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Product Removed"));
		JsfUtils.refreshComponentInDynamicTab("tabB1", getDynTabId());
		PrimeFaces.current().ajax().update("mainForm:msgs");
	}

	public void launchProductEditDynTab(Product prod) {
		DynTabManager.getCurrentInstance().launchDynamicTab("ProductEdit" + prod.getCode() + "DynTab",
				"Edit Product " + prod.getName(), "/WEB-INF/include/proizvodi/product_edit.xhtml",
				"ProductEdit" + prod.getCode(), // UniqueIdentifier taba
				true, cdibeans.ProductEditBean.class,
				java.util.Map.of("product", prod, "callerID", this.getUniqueIdentifier()));
	}

	@AccessCheck(resourceDisplayName = "New Product In DynTab", allowedRoles = { "ADMIN" })
	public void launchNewProductDynTab() {
		String randomUnique = UUID.randomUUID().toString();
		DynTabManager.getCurrentInstance().launchDynamicTab("ProductEdit" + randomUnique + "DynTab", "New Product",
				"/WEB-INF/include/proizvodi/product_edit.xhtml", "ProductEdit" + randomUnique, // UniqueIdentifier taba
				true, cdibeans.ProductEditBean.class, java.util.Map.of("callerID", this.getUniqueIdentifier()));
	}

	/**
	 *  
	 */
	@Override
	protected void onJobFlowReturn(String senderId, Object jobFlowReturnValue) {
		super.onJobFlowReturn(senderId, jobFlowReturnValue);
		log.debug("ProductsCrudBean.onJobFlowReturn(), senderId = {}, jobFlowReturnValue = {}", senderId,
				jobFlowReturnValue);
		if (senderId != null && senderId.contains("ProductEdit")) {
			// cast jobFlowReturnValue to the Product (we can do this, because we sent
			// Product as return
			// value through id="saveBtn" btn, product_edit.xhtml)
			Product prod = (Product) jobFlowReturnValue;
			// new or existing one?
			if (prod.getCode() == null) {// new
				prod.setCode(UUID.randomUUID().toString().replaceAll("-", "").substring(0, 9));
				this.products.addFirst(prod);
				FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Product Added"));
			} else {// existing one
				int index = products.indexOf(prod);
				if (index != -1) {
					products.set(index, prod);
					FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Product Updated"));
				}

			}
			// make product we worked with in dialog, as selected row in p:dataTable:
			setSelectedProduct(prod);
			// refresh dataTable:
			// refresh p:dataTable and msgs:
			JsfUtils.refreshComponentInDynamicTab("tabB1", getDynTabId());
			PrimeFaces.current().ajax().update("mainForm:msgs");

		}
	}

}

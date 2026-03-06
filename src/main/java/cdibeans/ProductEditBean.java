package cdibeans;

import java.util.Map;

import dyntabs.BaseDyntabCdiBean;
import dyntabs.scope.TabScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import model.Product;
import services.MyService;

@Named
@TabScoped
public class ProductEditBean extends BaseDyntabCdiBean {

	private Product productData;

	public Product getProductData() {
		return productData;
	}

	public void setProductData(Product productData) {
		this.productData = productData;
	}

	@Inject
	MyService service;
	private Product prodParam = null;

	@Override
	protected void accessPointMethod(Map parameters) {
		super.accessPointMethod(parameters);
		Product prodParam = (Product) getParameters().get("product");
		if (prodParam != null) {// edit product
			setProductData(prodParam);
		} else {// create new product
			setProductData(service.createNewProduct());
		}
	}

}

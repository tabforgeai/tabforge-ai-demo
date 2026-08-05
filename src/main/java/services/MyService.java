package services;

import model.Product;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MyService {
   public Product createNewProduct() {
	   return new Product();
   }
}

package services;

import model.Product;
import jakarta.ejb.Stateless;

@Stateless
public class MyService {
   public Product createNewProduct() {
	   return new Product();
   }
}

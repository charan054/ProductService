package com.example.productservice.service;

import com.example.productservice.entity.Product;
import com.example.productservice.exception.ItemNotFoundException;
import com.example.productservice.exception.PriceException;
import com.example.productservice.exception.StockException;
import com.example.productservice.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ProductService {
    @Autowired
    private ProductRepository productRepository;
    public Product save(Product product) {
        if(product.getProductPrice()<=0)
        {
            throw new PriceException("Price must be greater than 0");
        }
        if(product.getProductStock()<=0)
        {
            throw new PriceException("Stock must be greater than 0");
        }
        return productRepository.save(product);
    }
    public List<Product> findAll() {
        return productRepository.findAll();
    }
    public List<Product> findByCategory(String category) {
        return productRepository.findByproductCategory(category);
    }
    public List<Product> findByName(String name) {
        return productRepository.findByproductName(name);
    }
    public Product findById(int id) {
        Product product = productRepository.findById(id).orElseThrow( ()-> new ItemNotFoundException("Product not found"));
        return product;
    }

    public void deleteById(int id) {
        productRepository.deleteById(id);
    }
    public Product updateStock(int id, int stock) {
        Product product = productRepository.findById(id).orElseThrow( ()-> new ItemNotFoundException("Product not found"));
        int newStock = product.getProductStock() + stock;
        if(newStock<0)
        {
            throw new StockException("Stock is low");
        }
        product.setProductStock(newStock);
        return productRepository.save(product);
    }
    public Product updatePrice(int id, double price) {
        Product product = productRepository.findById(id).orElseThrow( ()-> new ItemNotFoundException("Product not found"));
        if(price<=0)
        {
            throw new PriceException("Price must be greater than 0");
        }
        product.setProductPrice(price);
        return productRepository.save(product);
    }
    public void delete(int id) {
        productRepository.deleteById(id);
    }
}

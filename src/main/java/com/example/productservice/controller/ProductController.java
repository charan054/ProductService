package com.example.productservice.controller;

import com.example.productservice.entity.Product;
import com.example.productservice.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product")
public class ProductController {
    @Autowired
    private ProductService productService;
    @PostMapping("/add")
    public Product addProduct(@RequestBody Product product){
        return productService.save(product);
    }
    @GetMapping("/all")
    public List<Product> getAllProducts(){
        return productService.findAll();
    }
    @GetMapping("/byId")
    public Product getProductById(@RequestParam Integer id){
        return productService.findById(id);
    }
    @GetMapping("/byName")
    public List<Product> getProductByName(@RequestParam String name){
        return productService.findByName(name);
    }
    @GetMapping("/byCategory")
    public List<Product> getProductByCategory(@RequestParam String category){
        return productService.findByCategory(category);
    }
    @PutMapping("/updatePrice")
    public Product updateProductPrice(@RequestParam Integer id, @RequestParam Double price){
        return productService.updatePrice(id, price);
    }
    @PutMapping("/updateStock")
    public Product updateProductStock(@RequestParam Integer id, @RequestParam Integer stock){
        return productService.updateStock(id, stock);
    }
    @DeleteMapping("/delete")
    public void deleteProduct(@RequestParam Integer id){
        productService.deleteById(id);
    }

}

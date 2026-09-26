package com.example.productservice.controller;

import com.example.productservice.entity.Category;
import com.example.productservice.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// /category/add stays under SecurityConfig's default "anyRequest().authenticated()" rule (X-Service-Key
// required); /category/all is added to its public GET allowlist alongside the other catalog-browsing endpoints.
@RestController
@RequestMapping("/category")
public class CategoryController {
    @Autowired
    private ProductService productService;

    @PostMapping("/add")
    public Category addCategory(@RequestBody Category category) {
        return productService.saveCategory(category);
    }

    @GetMapping("/all")
    public List<Category> getAllCategories() {
        return productService.getCategories();
    }
}

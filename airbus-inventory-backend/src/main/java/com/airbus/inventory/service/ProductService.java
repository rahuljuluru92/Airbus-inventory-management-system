package com.airbus.inventory.service;

import com.airbus.inventory.dto.PageResponse;
import com.airbus.inventory.dto.ProductRequest;
import com.airbus.inventory.dto.ProductResponse;
import com.airbus.inventory.exception.ResourceNotFoundException;
import com.airbus.inventory.model.Product;
import com.airbus.inventory.repository.ProductRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public PageResponse<ProductResponse> findAllPaged(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        List<ProductResponse> content = productRepository.findPage(safePage * safeSize, safeSize).stream()
                .map(this::toResponse).toList();
        long total = productRepository.count();
        return new PageResponse<>(content, safePage, safeSize, total);
    }

    public List<ProductResponse> findByCategory(String category) {
        return productRepository.findByCategory(category).stream().map(this::toResponse).toList();
    }

    public List<ProductResponse> findLowStock() {
        return productRepository.findLowStock().stream().map(this::toResponse).toList();
    }

    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id " + id));
        return toResponse(product);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(ProductRequest request) {
        Product product = toModel(request);
        String username = currentUsername();
        product.setCreatedBy(username);
        product.setUpdatedBy(username);
        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse update(Long id, ProductRequest request) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id " + id);
        }
        Product product = toModel(request);
        product.setUpdatedBy(currentUsername());
        productRepository.update(id, product);
        return findById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id " + id);
        }
        productRepository.deleteById(id);
    }

    private Product toModel(ProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setQuantity(request.getQuantity());
        product.setUnitPrice(request.getUnitPrice());
        product.setSupplier(request.getSupplier());
        product.setReorderLevel(request.getReorderLevel());
        return product;
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getCategory(), p.getQuantity(),
                p.getUnitPrice(), p.getSupplier(), p.getReorderLevel(), p.getCreatedBy(), p.getUpdatedBy(),
                p.getLastUpdated());
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}

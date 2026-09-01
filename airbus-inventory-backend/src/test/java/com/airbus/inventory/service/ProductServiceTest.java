package com.airbus.inventory.service;

import com.airbus.inventory.dto.PageResponse;
import com.airbus.inventory.dto.ProductRequest;
import com.airbus.inventory.dto.ProductResponse;
import com.airbus.inventory.exception.ResourceNotFoundException;
import com.airbus.inventory.model.Product;
import com.airbus.inventory.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Product sampleProduct(Long id) {
        return new Product(id, "Turbine Disc", "Engine Components", 5, new BigDecimal("98000.00"),
                "CFM International", 6, "admin", "admin", LocalDateTime.now());
    }

    @Test
    void findByIdReturnsMappedResponseWhenPresent() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct(1L)));

        ProductResponse response = productService.findById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Turbine Disc");
        assertThat(response.getCreatedBy()).isEqualTo("admin");
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void findAllPagedClampsPageAndSizeAndComputesTotalPages() {
        when(productRepository.findPage(0, 10)).thenReturn(List.of(sampleProduct(1L), sampleProduct(2L)));
        when(productRepository.count()).thenReturn(23L);

        PageResponse<ProductResponse> result = productService.findAllPaged(0, 10);

        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isEqualTo(23L);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void findAllPagedRejectsNegativePageAndOversizedSize() {
        when(productRepository.findPage(0, 100)).thenReturn(List.of());
        when(productRepository.count()).thenReturn(0L);

        PageResponse<ProductResponse> result = productService.findAllPaged(-5, 500);

        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(100);
        verify(productRepository).findPage(0, 100);
    }

    @Test
    void createSetsCreatedByAndUpdatedByFromSecurityContext() {
        ProductRequest request = new ProductRequest();
        request.setName("New Part");
        request.setCategory("Avionics");
        request.setQuantity(10);
        request.setUnitPrice(new BigDecimal("5.00"));
        request.setReorderLevel(2);

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(42L);
            p.setLastUpdated(LocalDateTime.now());
            return p;
        });

        ProductResponse response = productService.create(request);

        assertThat(response.getCreatedBy()).isEqualTo("admin");
        assertThat(response.getUpdatedBy()).isEqualTo("admin");
        assertThat(response.getId()).isEqualTo(42L);
    }

    @Test
    void updateThrowsWhenProductDoesNotExist() {
        when(productRepository.existsById(7L)).thenReturn(false);
        ProductRequest request = new ProductRequest();

        assertThatThrownBy(() -> productService.update(7L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, never()).update(anyLong(), any());
    }

    @Test
    void updateSetsUpdatedByAndDelegatesToRepository() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct(1L)));

        ProductRequest request = new ProductRequest();
        request.setName("Turbine Disc");
        request.setCategory("Engine Components");
        request.setQuantity(8);
        request.setUnitPrice(new BigDecimal("98000.00"));
        request.setReorderLevel(6);

        productService.update(1L, request);

        verify(productRepository).update(eq(1L), any(Product.class));
        verify(productRepository, times(1)).findById(1L);
    }

    @Test
    void deleteThrowsWhenProductDoesNotExist() {
        when(productRepository.existsById(5L)).thenReturn(false);

        assertThatThrownBy(() -> productService.delete(5L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(productRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteDelegatesToRepositoryWhenProductExists() {
        when(productRepository.existsById(5L)).thenReturn(true);

        productService.delete(5L);

        verify(productRepository).deleteById(5L);
    }

    @Test
    void findLowStockMapsRepositoryResults() {
        when(productRepository.findLowStock()).thenReturn(List.of(sampleProduct(3L)));

        List<ProductResponse> result = productService.findLowStock();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Turbine Disc");
    }
}

package com.airbus.inventory.repository;

import com.airbus.inventory.dto.CategoryCountResponse;
import com.airbus.inventory.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Product> ROW_MAPPER = (rs, rowNum) -> new Product(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getInt("quantity"),
            rs.getBigDecimal("unit_price"),
            rs.getString("supplier"),
            rs.getInt("reorder_level"),
            rs.getString("created_by"),
            rs.getString("updated_by"),
            rs.getTimestamp("last_updated").toLocalDateTime()
    );

    public List<Product> findPage(int offset, int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM products ORDER BY id LIMIT ? OFFSET ?", ROW_MAPPER, limit, offset);
    }

    public long count() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Long.class);
        return total != null ? total : 0L;
    }

    public List<Product> findByCategory(String category) {
        return jdbcTemplate.query(
                "SELECT * FROM products WHERE category = ? ORDER BY id",
                ROW_MAPPER, category);
    }

    public List<Product> findLowStock() {
        return jdbcTemplate.query(
                "SELECT * FROM products WHERE quantity <= reorder_level ORDER BY (reorder_level - quantity) DESC",
                ROW_MAPPER);
    }

    public Optional<Product> findById(Long id) {
        List<Product> results = jdbcTemplate.query(
                "SELECT * FROM products WHERE id = ?", ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    public Product save(Product product) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO products (name, category, quantity, unit_price, supplier, reorder_level, " +
                            "created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, product.getName());
            ps.setString(2, product.getCategory());
            ps.setInt(3, product.getQuantity());
            ps.setBigDecimal(4, product.getUnitPrice());
            ps.setString(5, product.getSupplier());
            ps.setInt(6, product.getReorderLevel());
            ps.setString(7, product.getCreatedBy());
            ps.setString(8, product.getUpdatedBy());
            return ps;
        }, keyHolder);

        Long newId = keyHolder.getKey().longValue();
        return findById(newId).orElseThrow();
    }

    public int update(Long id, Product product) {
        return jdbcTemplate.update(
                "UPDATE products SET name = ?, category = ?, quantity = ?, unit_price = ?, " +
                        "supplier = ?, reorder_level = ?, updated_by = ? WHERE id = ?",
                product.getName(), product.getCategory(), product.getQuantity(), product.getUnitPrice(),
                product.getSupplier(), product.getReorderLevel(), product.getUpdatedBy(), id);
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM products WHERE id = ?", id);
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public BigDecimal totalInventoryValue() {
        BigDecimal total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(quantity * unit_price), 0) FROM products", BigDecimal.class);
        return total != null ? total : BigDecimal.ZERO;
    }

    public long countLowStock() {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE quantity <= reorder_level", Long.class);
        return total != null ? total : 0L;
    }

    public List<CategoryCountResponse> countByCategory() {
        return jdbcTemplate.query(
                "SELECT category, COUNT(*) AS cnt FROM products GROUP BY category ORDER BY category",
                (rs, rowNum) -> new CategoryCountResponse(rs.getString("category"), rs.getLong("cnt")));
    }
}

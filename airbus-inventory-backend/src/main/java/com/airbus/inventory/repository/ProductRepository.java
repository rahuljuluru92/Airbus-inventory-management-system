package com.airbus.inventory.repository;

import com.airbus.inventory.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

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
            rs.getTimestamp("last_updated").toLocalDateTime()
    );

    public List<Product> findAll() {
        return jdbcTemplate.query("SELECT * FROM products ORDER BY id", ROW_MAPPER);
    }

    public List<Product> findByCategory(String category) {
        return jdbcTemplate.query(
                "SELECT * FROM products WHERE category = ? ORDER BY id",
                ROW_MAPPER, category);
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
                    "INSERT INTO products (name, category, quantity, unit_price, supplier, reorder_level) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, product.getName());
            ps.setString(2, product.getCategory());
            ps.setInt(3, product.getQuantity());
            ps.setBigDecimal(4, product.getUnitPrice());
            ps.setString(5, product.getSupplier());
            ps.setInt(6, product.getReorderLevel());
            return ps;
        }, keyHolder);

        Long newId = keyHolder.getKey().longValue();
        return findById(newId).orElseThrow();
    }

    public int update(Long id, Product product) {
        return jdbcTemplate.update(
                "UPDATE products SET name = ?, category = ?, quantity = ?, unit_price = ?, " +
                        "supplier = ?, reorder_level = ? WHERE id = ?",
                product.getName(), product.getCategory(), product.getQuantity(), product.getUnitPrice(),
                product.getSupplier(), product.getReorderLevel(), id);
    }

    public int deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM products WHERE id = ?", id);
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }
}

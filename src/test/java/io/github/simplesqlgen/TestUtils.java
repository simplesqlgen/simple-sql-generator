package io.github.simplesqlgen;

import javax.tools.JavaFileObject;
import com.google.testing.compile.JavaFileObjects;

/**
 * Test utilities for creating common test entities and repositories
 */
public class TestUtils {

    /**
     * Creates a basic User entity for testing
     */
    public static JavaFileObject createBasicUserEntity() {
        return JavaFileObjects.forSourceString("com.example.User", """
            package com.example;
            
            public class User {
                private Long id;
                private String name;
                private String email;
                private boolean active;
                private int age;
                private String firstName;
                private String lastName;
                
                public User() {}
                
                public User(String name, String email, boolean active) {
                    this.name = name;
                    this.email = email;
                    this.active = active;
                }
                
                public User(String name, String email, boolean active, int age, String firstName, String lastName) {
                    this.name = name;
                    this.email = email;
                    this.active = active;
                    this.age = age;
                    this.firstName = firstName;
                    this.lastName = lastName;
                }
                
                public Long getId() { return id; }
                public String getName() { return name; }
                public String getEmail() { return email; }
                public boolean isActive() { return active; }
                public int getAge() { return age; }
                public String getFirstName() { return firstName; }
                public String getLastName() { return lastName; }
                
                public void setId(Long id) { this.id = id; }
                public void setName(String name) { this.name = name; }
                public void setEmail(String email) { this.email = email; }
                public void setActive(boolean active) { this.active = active; }
                public void setAge(int age) { this.age = age; }
                public void setFirstName(String firstName) { this.firstName = firstName; }
                public void setLastName(String lastName) { this.lastName = lastName; }
                
                @Override
                public String toString() {
                    return "User{" +
                           "id=" + id +
                           ", name='" + name + '\\'' +
                           ", email='" + email + '\\'' +
                           ", active=" + active +
                           ", age=" + age +
                           '}';
                }
            }
            """);
    }

    /**
     * Creates a Product entity for testing different field types
     */
    public static JavaFileObject createProductEntity() {
        return JavaFileObjects.forSourceString("com.example.Product", """
            package com.example;
            
            import java.math.BigDecimal;
            import java.time.LocalDateTime;
            
            public class Product {
                private Long id;
                private String name;
                private String description;
                private BigDecimal price;
                private int quantity;
                private boolean available;
                private String category;
                private LocalDateTime createdAt;
                private LocalDateTime updatedAt;
                
                public Product() {}
                
                public Product(String name, BigDecimal price, int quantity) {
                    this.name = name;
                    this.price = price;
                    this.quantity = quantity;
                    this.available = true;
                    this.createdAt = LocalDateTime.now();
                    this.updatedAt = LocalDateTime.now();
                }
                
                public Long getId() { return id; }
                public String getName() { return name; }
                public String getDescription() { return description; }
                public BigDecimal getPrice() { return price; }
                public int getQuantity() { return quantity; }
                public boolean isAvailable() { return available; }
                public String getCategory() { return category; }
                public LocalDateTime getCreatedAt() { return createdAt; }
                public LocalDateTime getUpdatedAt() { return updatedAt; }
                
                public void setId(Long id) { this.id = id; }
                public void setName(String name) { this.name = name; }
                public void setDescription(String description) { this.description = description; }
                public void setPrice(BigDecimal price) { this.price = price; }
                public void setQuantity(int quantity) { this.quantity = quantity; }
                public void setAvailable(boolean available) { this.available = available; }
                public void setCategory(String category) { this.category = category; }
                public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
                public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
            }
            """);
    }

    /**
     * Creates a simple repository template with common method patterns
     */
    public static JavaFileObject createBasicRepository(String entityClass, String tableName) {
        return JavaFileObjects.forSourceString("com.example." + entityClass + "Repository", 
            String.format("""
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(entity = %s.class, tableName = \"%s\")
            public class %sRepository {
                
                public %s save(%s entity) {
                    return null;
                }
                
                public %s findById(Long id) {
                    return null;
                }
                
                public List<%s> findAll() {
                    return null;
                }
                
                public int deleteById(Long id) {
                    return 0;
                }
                
                public List<%s> findByName(String name) {
                    return null;
                }
                
                public long count() {
                    return 0;
                }
                
                public boolean existsById(Long id) {
                    return false;
                }
            }
            """, entityClass, tableName, entityClass, entityClass, entityClass, entityClass, entityClass, entityClass));
    }

    /**
     * Creates a repository with native queries only
     */
    public static JavaFileObject createNativeQueryOnlyRepository() {
        return JavaFileObjects.forSourceString("com.example.CustomQueryRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            import java.util.List;
            import java.util.Map;
            
            @SqlGenerator(nativeQueryOnly = true)
            public class CustomQueryRepository {
                
                @NativeQuery(\"SELECT * FROM users WHERE created_at > ?\")
                public List<Map<String, Object>> findRecentUsers(java.time.LocalDateTime since) {
                    return null;
                }
                
                @NativeQuery(\"SELECT COUNT(*) FROM users WHERE active = ?\")
                public long countActiveUsers(boolean active) {
                    return 0;
                }
                
                @NativeQuery(value = \"UPDATE users SET last_login = ? WHERE id = ?\", isUpdate = true)
                public int updateLastLogin(java.time.LocalDateTime lastLogin, Long userId) {
                    return 0;
                }
                
                @NativeQuery(value = \"DELETE FROM users WHERE active = false AND last_login < ?\", isUpdate = true)
                public int cleanupInactiveUsers(java.time.LocalDateTime cutoffDate) {
                    return 0;
                }
            }
            """);
    }

    /**
     * Creates a repository with complex method names for testing
     */
    public static JavaFileObject createComplexMethodRepository() {
        return JavaFileObjects.forSourceString("com.example.UserRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(entity = User.class, tableName = \"users\")
            public class UserRepository {
                
                public List<User> findByName(String name) { return null; }
                public List<User> findByEmail(String email) { return null; }
                public List<User> findByActive(boolean active) { return null; }
                
                public List<User> findByNameAndAge(String name, int age) { return null; }
                public List<User> findByActiveAndEmailAndName(boolean active, String email, String name) { return null; }
                
                public List<User> findByAgeGreaterThan(int age) { return null; }
                public List<User> findByAgeLessThan(int age) { return null; }
                public List<User> findByAgeGreaterThanEqual(int age) { return null; }
                public List<User> findByAgeLessThanEqual(int age) { return null; }
                
                public List<User> findByNameAndAgeGreaterThanAndActive(String name, int age, boolean active) { return null; }
                public List<User> findByActiveAndAgeGreaterThanAndEmailAndName(boolean active, int age, String email, String name) { return null; }
                
                public long countByActive(boolean active) { return 0; }
                public long countByName(String name) { return 0; }
                public long countByAgeGreaterThan(int age) { return 0; }
                
                public boolean existsByEmail(String email) { return false; }
                public boolean existsByName(String name) { return false; }
                public boolean existsByActiveAndAge(boolean active, int age) { return false; }
                
                public int deleteById(Long id) { return 0; }
                public int deleteByActive(boolean active) { return 0; }
                public int deleteByAgeGreaterThan(int age) { return 0; }
                
                public int save(User user) { return 0; }
                public int update(User user) { return 0; }
            }
            """);
    }
}
package io.github.simplesqlgen.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.google.testing.compile.Compiler;
import io.github.simplesqlgen.processor.SqlProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * Integration tests for SqlProcessor using Google Testing Compile
 * Tests the complete annotation processing pipeline
 */
class SqlProcessorIntegrationTest {

    @Test
    @DisplayName("Should compile repository with @SqlGenerator annotation successfully")
    void testBasicRepositoryCompilation() {
        // Given
        JavaFileObject userEntity = JavaFileObjects.forSourceString("com.example.User", """
            package com.example;
            
            public class User {
                private Long id;
                private String name;
                private String email;
                private boolean active;
                
                // Constructors
                public User() {}
                
                public User(String name, String email, boolean active) {
                    this.name = name;
                    this.email = email;
                    this.active = active;
                }
                
                // Getters and Setters
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
                
                public boolean isActive() { return active; }
                public void setActive(boolean active) { this.active = active; }
            }
            """);

        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.UserRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                
                public List<User> findByName(String name) {
                    return null;
                }
                
                public User findByEmail(String email) {
                    return null;
                }
                
                public List<User> findByActive(boolean active) {
                    return null;
                }
                
                public long countByActive(boolean active) {
                    return 0;
                }
                
                public boolean existsByEmail(String email) {
                    return false;
                }
                
                public int deleteById(Long id) {
                    return 0;
                }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repository);

        // Then
        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("Should compile repository with @NativeQuery annotations successfully")
    void testNativeQueryRepositoryCompilation() {
        // Given
        JavaFileObject userEntity = JavaFileObjects.forSourceString("com.example.User", """
            package com.example;
            
            public class User {
                private Long id;
                private String name;
                private String email;
                
                public User() {}
                
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
            }
            """);

        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.UserRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            import java.util.List;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                
                @NativeQuery(value = "SELECT * FROM users WHERE name = ?", resultType = User.class)
                public List<User> findUsersByName(String name) {
                    return null;
                }
                
                @NativeQuery("SELECT COUNT(*) FROM users WHERE email = ?")
                public long countByEmail(String email) {
                    return 0;
                }
                
                @NativeQuery(value = "UPDATE users SET name = ? WHERE id = ?", isUpdate = true)
                public int updateUserName(String name, Long id) {
                    return 0;
                }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repository);

        // Then
        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("Should compile native-query-only repository successfully")
    void testNativeQueryOnlyRepositoryCompilation() {
        // Given
        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.CustomQueryRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            import java.util.List;
            import java.util.Map;
            
            @SqlGenerator(nativeQueryOnly = true)
            public class CustomQueryRepository {
                
                @NativeQuery("SELECT custom_function(?) as result")
                public String executeCustomFunction(String input) {
                    return null;
                }
                
                @NativeQuery("SELECT u.name, p.title FROM users u LEFT JOIN profiles p ON u.id = p.user_id")
                public List<Map<String, Object>> getComplexJoinQuery() {
                    return null;
                }
                
                @NativeQuery(value = "UPDATE statistics SET count = count + 1 WHERE type = ?", isUpdate = true)
                public void incrementStatistics(String type) {
                }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(repository);

        // Then
        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("Should handle various return types correctly")
    void testVariousReturnTypes() {
        // Given
        JavaFileObject userEntity = JavaFileObjects.forSourceString("com.example.User", """
            package com.example;
            
            public class User {
                private Long id;
                private String name;
                
                public User() {}
                
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
            }
            """);

        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.UserRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            import java.util.Optional;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                
                // List return type
                public List<User> findByName(String name) {
                    return null;
                }
                
                // Single entity return type
                public User findById(Long id) {
                    return null;
                }
                
                // Primitive return types
                public long countByName(String name) {
                    return 0;
                }
                
                public boolean existsByName(String name) {
                    return false;
                }
                
                public int deleteByName(String name) {
                    return 0;
                }
                
                // Void return type (for updates)
                public void updateNameById(String name, Long id) {
                }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repository);

        // Then
        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("Should handle complex method names correctly")
    void testComplexMethodNames() {
        // Given
        JavaFileObject userEntity = JavaFileObjects.forSourceString("com.example.User", """
            package com.example;
            
            public class User {
                private Long id;
                private String name;
                private int age;
                private boolean active;
                private String email;
                
                public User() {}
                
                // Getters
                public Long getId() { return id; }
                public String getName() { return name; }
                public int getAge() { return age; }
                public boolean isActive() { return active; }
                public String getEmail() { return email; }
                
                // Setters
                public void setId(Long id) { this.id = id; }
                public void setName(String name) { this.name = name; }
                public void setAge(int age) { this.age = age; }
                public void setActive(boolean active) { this.active = active; }
                public void setEmail(String email) { this.email = email; }
            }
            """);

        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.UserRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                
                // Simple conditions
                public List<User> findByName(String name) {
                    return null;
                }
                
                // Multiple conditions with And
                public List<User> findByNameAndAge(String name, int age) {
                    return null;
                }
                
                // Comparison operators
                public List<User> findByAgeGreaterThan(int age) {
                    return null;
                }
                
                public List<User> findByAgeLessThanEqual(int age) {
                    return null;
                }
                
                // Complex combinations
                public List<User> findByNameAndAgeGreaterThanAndActive(String name, int age, boolean active) {
                    return null;
                }
                
                // Different operations
                public long countByActive(boolean active) {
                    return 0;
                }
                
                public boolean existsByEmailAndActive(String email, boolean active) {
                    return false;
                }
                
                public int deleteByActiveAndAgeGreaterThan(boolean active, int age) {
                    return 0;
                }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repository);

        // Then
        assertThat(compilation).succeededWithoutWarnings();
    }
}
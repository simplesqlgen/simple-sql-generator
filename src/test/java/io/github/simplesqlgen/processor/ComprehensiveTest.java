package io.github.simplesqlgen.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import io.github.simplesqlgen.TestUtils;
import io.github.simplesqlgen.processor.SqlProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * Comprehensive test scenarios covering various use cases and edge cases
 */
class ComprehensiveTest {

    @Test
    @DisplayName("Should handle all basic CRUD operations")
    void testBasicCrudOperations() {
        // Given
        JavaFileObject userEntity = TestUtils.createBasicUserEntity();
        JavaFileObject repository = TestUtils.createBasicRepository("User", "users");

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repository);

        // Then
        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("Should handle complex Product entity with various field types")
    void testComplexEntityTypes() {
        // Given
        JavaFileObject productEntity = TestUtils.createProductEntity();
        JavaFileObject repository = TestUtils.createBasicRepository("Product", "products");

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(productEntity, repository);

        // Then
        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("Should handle native query only repositories")
    void testNativeQueryOnlyRepositories() {
        // Given
        JavaFileObject repository = TestUtils.createNativeQueryOnlyRepository();

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(repository);

        // Then
        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("Should handle complex method naming patterns")
    void testComplexMethodNames() {
        // Given
        JavaFileObject userEntity = TestUtils.createBasicUserEntity();
        JavaFileObject repository = TestUtils.createComplexMethodRepository();

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repository);

        // Then
        assertThat(compilation).succeededWithoutWarnings();
    }

    @ParameterizedTest
    @ValueSource(strings = {"users", "user_accounts", "app_users", "system_users"})
    @DisplayName("Should handle different table naming conventions")
    void testDifferentTableNames(String tableName) {
        // Given
        JavaFileObject userEntity = TestUtils.createBasicUserEntity();
        JavaFileObject repository = TestUtils.createBasicRepository("User", tableName);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repository);

        // Then
        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("Should handle mixed repository with both generated and native queries")
    void testMixedRepository() {
        // Given
        JavaFileObject userEntity = TestUtils.createBasicUserEntity();
        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.MixedRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            import java.util.List;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class MixedRepository {
                
                // Generated queries
                public List<User> findByName(String name) {
                    return null;
                }
                
                public long countByActive(boolean active) {
                    return 0;
                }
                
                public boolean existsByEmail(String email) {
                    return false;
                }
                
                // Native queries
                @NativeQuery(value = "SELECT * FROM users WHERE name LIKE ? ORDER BY created_at DESC LIMIT ?", resultType = User.class)
                public List<User> searchByNameWithLimit(String namePattern, int limit) {
                    return null;
                }
                
                @NativeQuery(value = "UPDATE users SET active = false WHERE last_login < ?", isUpdate = true)
                public int deactivateInactiveUsers(java.time.LocalDateTime cutoffDate) {
                    return 0;
                }
                
                @NativeQuery("SELECT COUNT(DISTINCT email) FROM users WHERE active = ?")
                public long countUniqueActiveEmails(boolean active) {
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
    @DisplayName("Should handle repository with void methods")
    void testVoidMethods() {
        // Given
        JavaFileObject userEntity = TestUtils.createBasicUserEntity();
        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.UserRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                
                @NativeQuery(value = "UPDATE users SET active = ? WHERE id = ?", isUpdate = true)
                public void updateActiveStatus(boolean active, Long id) {
                }
                
                @NativeQuery(value = "DELETE FROM users WHERE active = false", isUpdate = true)
                public void cleanupInactiveUsers() {
                }
                
                @NativeQuery(value = "INSERT INTO audit_log (action, user_id) VALUES (?, ?)", isUpdate = true)
                public void logUserAction(String action, Long userId) {
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
    @DisplayName("Should handle empty repository class")
    void testEmptyRepository() {
        // Given
        JavaFileObject userEntity = TestUtils.createBasicUserEntity();
        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.UserRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                // Empty repository - should still compile successfully
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
    @DisplayName("Should handle repository with only constructor and fields")
    void testRepositoryWithConstructorAndFields() {
        // Given
        JavaFileObject userEntity = TestUtils.createBasicUserEntity();
        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.UserRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                
                private String customProperty;
                
                public UserRepository() {
                    this.customProperty = "default";
                }
                
                public UserRepository(String customProperty) {
                    this.customProperty = customProperty;
                }
                
                public List<User> findByName(String name) {
                    return null;
                }
                
                public String getCustomProperty() {
                    return customProperty;
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
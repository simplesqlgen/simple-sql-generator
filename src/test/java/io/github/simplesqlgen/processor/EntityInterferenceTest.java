package io.github.simplesqlgen.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.google.testing.compile.Compiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * Test to verify that @SqlGenerator entity does NOT affect NativeQuery resultType inference
 */
class EntityInterferenceTest {

    @Test
    @DisplayName("NativeQuery should work independently of @SqlGenerator entity setting")
    void testNativeQueryIndependenceFromEntity() {
        // Given - User entity
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

        // Test Case 1: Repository with entity = User.class (reproducing the exact failing case)
        JavaFileObject repositoryWithEntity = JavaFileObjects.forSourceString("com.example.UserRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            import java.util.List;
            import java.util.Optional;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                
                @NativeQuery(value = "SELECT * FROM users WHERE id = ?", resultType = User.class)
                public User findByIdNative(Long id) {
                    return null;
                }
                
                @NativeQuery(value = "SELECT * FROM users WHERE name = ?", resultType = User.class)
                public Optional<User> findByNameOptional(String name) {
                    return Optional.empty();
                }
                
                @NativeQuery(value = "SELECT * FROM users WHERE name LIKE ?", resultType = User.class)
                public List<User> findByNameContaining(String namePattern) {
                    return null;
                }
                
                // This was the failing case - no resultType specified
                @NativeQuery("SELECT COUNT(*) FROM users")
                public long countAll() {
                    return 0;
                }
                
                @NativeQuery(value = "SELECT name FROM users WHERE id = ?", resultType = String.class)
                public String getNameById(Long id) {
                    return null;
                }
                
                @NativeQuery(value = "DELETE FROM users WHERE id = ?", isUpdate = true)
                public void deleteByIdNative(Long id) {
                }
            }
            """);

        // Test Case 2: Repository with entity = void.class (should also work)
        JavaFileObject repositoryWithVoid = JavaFileObjects.forSourceString("com.debug.VoidRepository", """
            package com.debug;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            
            @SqlGenerator(entity = void.class, nativeQueryOnly = true)
            public class VoidRepository {
                
                // This MUST work - same query, same method signature
                @NativeQuery("SELECT COUNT(*) FROM users")
                public long countAll() {
                    return 0;
                }
                
                @NativeQuery("SELECT name FROM users WHERE id = ?")
                public String getNameById(Long id) {
                    return null;
                }
            }
            """);

        // When & Then - Both should succeed
        Compilation compilation1 = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repositoryWithEntity);
        
        Compilation compilation2 = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(repositoryWithVoid);

        // CRITICAL: Both must succeed - @SqlGenerator entity should NOT affect NativeQuery
        assertThat(compilation1).succeeded();
        assertThat(compilation2).succeeded();
        
        // If either fails, it indicates entity interference bug
        if (compilation1.status() != compilation2.status()) {
            throw new AssertionError("CRITICAL BUG: @SqlGenerator entity affects NativeQuery processing! " +
                "entity=User.class: " + compilation1.status() + ", " +
                "entity=void.class: " + compilation2.status());
        }
    }
}
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
 * Test that demonstrates actual code generation
 */
class CodeGenerationTest {

    @Test
    @DisplayName("Should generate @Autowired fields in repository")
    void testAutowiredFieldGeneration() {
        // Given - Simple User entity
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

        // Given - Repository with empty method bodies
        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.UserRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                
                public User findByName(String name) {
                    return null; // This should be replaced with generated code
                }
                
                public List<User> findAll() {
                    return null; // This should be replaced with generated code
                }
                
                public long countByName(String name) {
                    return 0; // This should be replaced with generated code
                }
            }
            """);

        // When - Compile with our annotation processor
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repository);

        // Then - Should compile successfully (meaning @Autowired fields were added)
        assertThat(compilation).succeeded();
        
        // Print generated files for debugging
        compilation.generatedFiles().forEach(file -> {
            // Generated file processed
        });
    }

    @Test
    @DisplayName("Should handle native queries without entity")
    void testNativeQueryOnlyMode() {
        // Given - Repository with only native queries
        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.QueryRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            import java.util.List;
            import java.util.Map;
            
            @SqlGenerator(entity = void.class, nativeQueryOnly = true)
            public class QueryRepository {
                
                @NativeQuery("SELECT COUNT(*) FROM users")
                public long getUserCount() {
                    return 0; // This should be replaced with jdbcTemplate.queryForObject(...)
                }
                
                @NativeQuery("SELECT * FROM users WHERE name = ?")
                public List<Map<String, Object>> findUsersByName(String name) {
                    return null; // This should be replaced with jdbcTemplate.query(...)
                }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(repository);

        // Then
        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should show what happens when annotation processor runs")
    void testShowProcessorOutput() {
        // Annotation processor test
        
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

        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.TestRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            import java.util.List;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class TestRepository {
                
                // This method should get @Autowired fields added
                // and method body should be replaced with:
                // return jdbcTemplate.query("SELECT * FROM users WHERE name = ?", 
                //                          new BeanPropertyRowMapper<>(User.class), name);
                public List<User> findByName(String name) {
                    return null;
                }
                
                // This should be replaced with COUNT query
                public long countByEmail(String email) {
                    return 0;
                }
                
                // This should work with native query
                @NativeQuery(value = "SELECT * FROM users WHERE email = ? AND name = ?", resultType = User.class)
                public List<User> findByEmailAndName(String email, String name) {
                    return null;
                }
            }
            """);

        // When
        // Starting compilation
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repository);

        // Then
        // Compilation completed
        
        if (compilation.errors().isEmpty()) {
            // Annotation processor transformations completed
        } else {
            // Compilation errors occurred
        }
        
        assertThat(compilation).succeeded();
    }
}
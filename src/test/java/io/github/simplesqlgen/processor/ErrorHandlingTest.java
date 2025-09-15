package io.github.simplesqlgen.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.google.testing.compile.Compiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * Error handling and exception cases test
 * Verifies robustness as an open-source library
 */
class ErrorHandlingTest {

    @Test
    @DisplayName("Should handle invalid annotation configuration appropriately")
    void testInvalidAnnotationConfiguration() {
        // Given - Using @SqlGenerator without entity
        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.InvalidRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            
            @SqlGenerator(tableName = "users")  // No entity specified
            public class InvalidRepository {
                public void findByName(String name) {
                }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(repository);

        // Then - Should compile successfully with warnings or appropriate handling
        assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("Should handle queries with non-existent fields appropriately")
    void testNonExistentFieldInQuery() {
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
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                // Query with non-existent field 'nonExistentField'
                public List<User> findByNonExistentField(String value) {
                    return null;
                }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repository);

        // Then - Should compile successfully with possible warnings
        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should handle invalid method naming patterns appropriately")
    void testInvalidMethodNamingPattern() {
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
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                // Unsupported pattern
                public List<User> selectAllWhereNameEquals(String name) {
                    return null;
                }
                
                // Empty method name
                public List<User> find() {
                    return null;
                }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repository);

        // Then
        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should handle circular reference entities")
    void testCircularReferenceEntity() {
        // Given
        JavaFileObject userEntity = JavaFileObjects.forSourceString("com.example.User", """
            package com.example;
            
            public class User {
                private Long id;
                private String name;
                private Profile profile;  // Potential circular reference
                
                public User() {}
                
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                
                public Profile getProfile() { return profile; }
                public void setProfile(Profile profile) { this.profile = profile; }
            }
            """);

        JavaFileObject profileEntity = JavaFileObjects.forSourceString("com.example.Profile", """
            package com.example;
            
            public class Profile {
                private Long id;
                private User user;  // Circular reference
                
                public Profile() {}
                
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                
                public User getUser() { return user; }
                public void setUser(User user) { this.user = user; }
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
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, profileEntity, repository);

        // Then - Should be able to process basic fields even with circular references
        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should handle generic type parameters")
    void testGenericTypeParameters() {
        // Given
        JavaFileObject userEntity = JavaFileObjects.forSourceString("com.example.User", """
            package com.example;
            
            import java.util.List;
            import java.util.Map;
            
            public class User {
                private Long id;
                private String name;
                private List<String> tags;
                private Map<String, Object> attributes;
                
                public User() {}
                
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                
                public List<String> getTags() { return tags; }
                public void setTags(List<String> tags) { this.tags = tags; }
                
                public Map<String, Object> getAttributes() { return attributes; }
                public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
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
                
                public User save(User user) {
                    return null;
                }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repository);

        // Then
        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Should handle null parameters gracefully")
    void testNullParameterHandling() {
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
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                // Parameters that can be null
                public List<User> findByName(String name) {  // name can be null
                    return null;
                }
                
                public User save(User user) {  // user can be null
                    return null;
                }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repository);

        // Then
        assertThat(compilation).succeeded();
    }
}
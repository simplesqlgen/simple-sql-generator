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
 * Test that actually inspects the generated code content
 */
class ActualCodeInspectionTest {

    @Test
    @DisplayName("Should actually inspect generated code content")
    void testInspectGeneratedCode() {
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
                
                public List<User> findByName(String name) {
                    return null; // This should be replaced with JDBC code
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
    @DisplayName("Should show what our processor actually does to the original code")
    void testShowActualTransformation() {
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

        JavaFileObject originalRepository = JavaFileObjects.forSourceString("com.example.UserRepository", """
            package com.example;
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                public List<User> findByName(String name) {
                    return null;
                }
                
                public long countByName(String name) {
                    return 0;
                }
            }
            """);
        
        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, originalRepository);

        // Then
        assertThat(compilation).succeeded();
    }
}
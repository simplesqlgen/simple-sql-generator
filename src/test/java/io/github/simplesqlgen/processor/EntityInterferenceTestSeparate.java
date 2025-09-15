package io.github.simplesqlgen.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.google.testing.compile.Compiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * Separate tests to isolate the issue
 */
class EntityInterferenceTestSeparate {

    @Test
    @DisplayName("Test only void.class repository")
    void testVoidClassRepository() {
        // Test Case: Repository with entity = void.class (should work)
        JavaFileObject repositoryWithVoid = JavaFileObjects.forSourceString("com.debug.VoidRepository", """
            package com.debug;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            
            @SqlGenerator(entity = void.class, nativeQueryOnly = true)
            public class VoidRepository {
                
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

        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(repositoryWithVoid);

        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Test User.class repository")
    void testUserClassRepository() {
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

        JavaFileObject repositoryWithEntity = JavaFileObjects.forSourceString("com.example.UserRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                
                @NativeQuery("SELECT COUNT(*) FROM users")
                public long countAll() {
                    return 0;
                }
            }
            """);

        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, repositoryWithEntity);

        if (compilation.status() == Compilation.Status.SUCCESS) {
            // User.class repository works
        } else {
            // User.class repository compilation failed
        }

        assertThat(compilation).succeeded();
    }
}
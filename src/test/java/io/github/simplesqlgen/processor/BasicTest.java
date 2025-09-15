package io.github.simplesqlgen.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.google.testing.compile.Compiler;
import io.github.simplesqlgen.processor.SqlProcessor;
import io.github.simplesqlgen.processor.sql.SqlGenerator;
import io.github.simplesqlgen.enums.NamingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import org.assertj.core.api.Assertions;

/**
 * Basic tests for core functionality
 */
class BasicTest {

    private SqlGenerator sqlGenerator;

    @BeforeEach
    void setUp() {
        sqlGenerator = new SqlGenerator();
    }

    @Test
    @DisplayName("Should parse simple findBy method names")
    void testSimpleFindByParsing() {
        // Given
        String methodName = "findByName";

        // When
        SqlGenerator.QueryMethodInfo info = sqlGenerator.parseQueryMethodName(methodName);

        // Then
        Assertions.assertThat(info.getOperation()).isEqualTo("find");
        Assertions.assertThat(info.getFields()).containsExactly("name");
    }

    @Test
    @DisplayName("Should parse countBy method names")
    void testCountByParsing() {
        // Given
        String methodName = "countByActive";

        // When
        SqlGenerator.QueryMethodInfo info = sqlGenerator.parseQueryMethodName(methodName);

        // Then
        Assertions.assertThat(info.getOperation()).isEqualTo("count");
        Assertions.assertThat(info.getFields()).containsExactly("active");
    }

    @Test
    @DisplayName("Should set naming strategy")
    void testNamingStrategy() {
        // Given
        sqlGenerator.setNamingStrategy(NamingStrategy.CAMEL_CASE);
        
        // When & Then - just verify no exception is thrown
        Assertions.assertThat(sqlGenerator).isNotNull();
    }

    @Test
    @DisplayName("Should compile simple repository successfully")
    void testSimpleRepositoryCompilation() {
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
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                
                public User findByName(String name) {
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
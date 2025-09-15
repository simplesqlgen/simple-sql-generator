package io.github.simplesqlgen.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.google.testing.compile.Compiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * Tests for NativeQuery annotation features
 */
class NativeQueryFeatureTest {

    @Test
    @DisplayName("Test named parameters")
    void testNamedParameters() {
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
            import io.github.simplesqlgen.annotation.NativeQuery;
            import io.github.simplesqlgen.annotation.Param;
            import io.github.simplesqlgen.enums.ParameterType;
            import java.util.List;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                
                @NativeQuery(
                    value = "SELECT * FROM users WHERE name = :userName",
                    parameterType = ParameterType.NAMED,
                    resultType = User.class
                )
                public List<User> findByNameNamed(@Param("userName") String name) {
                    return null;
                }
                
                @NativeQuery(
                    value = "UPDATE users SET name = :newName WHERE id = :userId",
                    isUpdate = true,
                    parameterType = ParameterType.NAMED
                )
                public int updateName(@Param("newName") String newName, @Param("userId") Long userId) {
                    return 0;
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
    @DisplayName("Test positional parameters")
    void testPositionalParameters() {
        // Given
        JavaFileObject userEntity = JavaFileObjects.forSourceString("com.example.User", """
            package com.example;
            
            public class User {
                private Long id;
                private String name;
                private Integer age;
                
                public User() {}
                
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                
                public Integer getAge() { return age; }
                public void setAge(Integer age) { this.age = age; }
            }
            """);

        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.UserRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            import io.github.simplesqlgen.enums.ParameterType;
            import java.util.List;
            
            @SqlGenerator(entity = User.class, tableName = "users")
            public class UserRepository {
                
                @NativeQuery(
                    value = "SELECT * FROM users WHERE age > ?",
                    parameterType = ParameterType.POSITIONAL,
                    resultType = User.class
                )
                public List<User> findByAgeGreaterThan(Integer minAge) {
                    return null;
                }
                
                @NativeQuery(
                    value = "SELECT COUNT(*) FROM users WHERE age BETWEEN ? AND ?",
                    parameterType = ParameterType.POSITIONAL
                )
                public long countByAgeBetween(Integer minAge, Integer maxAge) {
                    return 0;
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
    @DisplayName("Test various return types")
    void testVariousReturnTypes() {
        // Given - Test primitive and string return types only
        JavaFileObject repository = JavaFileObjects.forSourceString("com.test.SimpleRepository", """
            package com.test;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            
            @SqlGenerator(entity = void.class, nativeQueryOnly = true)
            public class SimpleRepository {
                
                @NativeQuery(value = "SELECT COUNT(*) FROM users", resultType = Long.class)
                public long countAll() {
                    return 0;
                }
                
                @NativeQuery(value = "SELECT name FROM users WHERE id = ?", resultType = String.class)
                public String getNameById(Long id) {
                    return null;
                }
                
                @NativeQuery(value = "SELECT COUNT(*) FROM users WHERE active = ?", resultType = Integer.class)
                public int countActiveUsers(boolean active) {
                    return 0;
                }
                
                @NativeQuery(value = "DELETE FROM users WHERE id = ?", isUpdate = true)
                public void deleteByIdNative(Long id) {
                }
                
                @NativeQuery(value = "UPDATE users SET active = ? WHERE id = ?", isUpdate = true)
                public int updateUserStatus(boolean active, Long id) {
                    return 0;
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
}
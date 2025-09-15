package io.github.simplesqlgen.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.google.testing.compile.Compiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * Annotation configuration options test
 * Verifies that various configuration options of @SqlGenerator are handled correctly
 */
class AnnotationConfigTest {

    @Test
    @DisplayName("Test various naming strategies")
    void testNamingStrategies() {
        // Given
        JavaFileObject userEntity = JavaFileObjects.forSourceString("com.example.User", """
            package com.example;
            
            public class User {
                private Long id;
                private String firstName;
                private String lastName;
                private String emailAddress;
                private Boolean isActive;
                
                public User() {}
                
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                
                public String getFirstName() { return firstName; }
                public void setFirstName(String firstName) { this.firstName = firstName; }
                
                public String getLastName() { return lastName; }
                public void setLastName(String lastName) { this.lastName = lastName; }
                
                public String getEmailAddress() { return emailAddress; }
                public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
                
                public Boolean getIsActive() { return isActive; }
                public void setIsActive(Boolean isActive) { this.isActive = isActive; }
            }
            """);

        // SNAKE_CASE test
        JavaFileObject snakeCaseRepository = JavaFileObjects.forSourceString("com.example.SnakeCaseRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.enums.NamingStrategy;
            import java.util.List;
            
            @SqlGenerator(
                entity = User.class, 
                tableName = "users",
                namingStrategy = NamingStrategy.SNAKE_CASE
            )
            public class SnakeCaseRepository {
                public List<User> findByFirstName(String firstName) { return null; }
                public List<User> findByEmailAddress(String email) { return null; }
                public boolean existsByIsActive(Boolean active) { return false; }
            }
            """);

        // CAMEL_CASE test
        JavaFileObject camelCaseRepository = JavaFileObjects.forSourceString("com.example.CamelCaseRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.enums.NamingStrategy;
            import java.util.List;
            
            @SqlGenerator(
                entity = User.class, 
                tableName = "users",
                namingStrategy = NamingStrategy.CAMEL_CASE
            )
            public class CamelCaseRepository {
                public List<User> findByFirstName(String firstName) { return null; }
                public List<User> findByEmailAddress(String email) { return null; }
                public boolean existsByIsActive(Boolean active) { return false; }
            }
            """);

        // When & Then - each naming strategy should compile successfully
        Compilation snakeCompilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, snakeCaseRepository);
        assertThat(snakeCompilation).succeeded();

        Compilation camelCompilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, camelCaseRepository);
        assertThat(camelCompilation).succeeded();
    }

    @Test
    @DisplayName("Test native query only mode")
    void testNativeQueryOnlyMode() {
        // Given
        JavaFileObject nativeOnlyRepository = JavaFileObjects.forSourceString("com.example.NativeOnlyRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import io.github.simplesqlgen.annotation.NativeQuery;
            import java.util.List;
            import java.util.Map;
            
            @SqlGenerator(
                entity = void.class,
                nativeQueryOnly = true
            )
            public class NativeOnlyRepository {
                
                @NativeQuery("SELECT * FROM custom_view WHERE status = ?")
                public List<Map<String, Object>> getCustomData(String status) {
                    return null;
                }
                
                @NativeQuery("SELECT COUNT(*) FROM analytics WHERE date >= ?")
                public long getAnalyticsCount(java.time.LocalDate date) {
                    return 0;
                }
                
                @NativeQuery(value = "UPDATE settings SET value = ? WHERE key = ?", isUpdate = true)
                public void updateSetting(String value, String key) {
                }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(nativeOnlyRepository);

        // Then
        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Test transaction enabled configuration")
    void testTransactionEnabled() {
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

        JavaFileObject transactionalRepository = JavaFileObjects.forSourceString("com.example.TransactionalRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(
                entity = User.class,
                tableName = "users",
                enableTransactions = true
            )
            public class TransactionalRepository {
                
                public User save(User user) {
                    return null;
                }
                
                public void deleteById(Long id) {
                }
                
                public void updateNameById(String name, Long id) {
                }
                
                public List<User> findByName(String name) {
                    return null;
                }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, transactionalRepository);

        // Then
        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Test custom table names")
    void testCustomTableNames() {
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

        // Test schema.table format
        JavaFileObject schemaTableRepository = JavaFileObjects.forSourceString("com.example.SchemaTableRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(entity = User.class, tableName = "app_schema.users")
            public class SchemaTableRepository {
                public List<User> findByName(String name) { return null; }
            }
            """);
        
        // Test table name with underscores
        JavaFileObject underscoreTableRepository = JavaFileObjects.forSourceString("com.example.UnderscoreTableRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(entity = User.class, tableName = "user_accounts")
            public class UnderscoreTableRepository {
                public List<User> findByName(String name) { return null; }
            }
            """);

        // When & Then
        Compilation schemaCompilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, schemaTableRepository);
        assertThat(schemaCompilation).succeeded();

        Compilation underscoreCompilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, underscoreTableRepository);
        assertThat(underscoreCompilation).succeeded();
    }

    @Test
    @DisplayName("Test default configuration")
    void testDefaultConfiguration() {
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

        // Minimal configuration (using defaults)
        JavaFileObject defaultRepository = JavaFileObjects.forSourceString("com.example.DefaultRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(entity = User.class)  // tableName should be auto-inferred
            public class DefaultRepository {
                public List<User> findByName(String name) { return null; }
                public User save(User user) { return null; }
                public long countByName(String name) { return 0; }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(userEntity, defaultRepository);

        // Then
        assertThat(compilation).succeeded();
    }
}
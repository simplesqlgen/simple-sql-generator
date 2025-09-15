package io.github.simplesqlgen.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.google.testing.compile.Compiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * Tests for various data type support
 */
class DataTypeSupportTest {

    @Test
    @DisplayName("Test primitive data types")
    void testPrimitiveDataTypes() {
        // Given
        JavaFileObject entity = JavaFileObjects.forSourceString("com.example.PrimitiveEntity", """
            package com.example;
            
            public class PrimitiveEntity {
                private int intField;
                private long longField;
                private boolean boolField;
                private double doubleField;
                private String stringField;
                
                public PrimitiveEntity() {}
                
                public int getIntField() { return intField; }
                public void setIntField(int intField) { this.intField = intField; }
                
                public long getLongField() { return longField; }
                public void setLongField(long longField) { this.longField = longField; }
                
                public boolean isBoolField() { return boolField; }
                public void setBoolField(boolean boolField) { this.boolField = boolField; }
                
                public double getDoubleField() { return doubleField; }
                public void setDoubleField(double doubleField) { this.doubleField = doubleField; }
                
                public String getStringField() { return stringField; }
                public void setStringField(String stringField) { this.stringField = stringField; }
            }
            """);

        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.PrimitiveRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(entity = PrimitiveEntity.class, tableName = "primitive_entity")
            public class PrimitiveRepository {
                public List<PrimitiveEntity> findByIntField(int value) { return null; }
                public List<PrimitiveEntity> findByBoolField(boolean value) { return null; }
                public long countByStringField(String value) { return 0; }
                public PrimitiveEntity save(PrimitiveEntity entity) { return null; }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(entity, repository);

        // Then
        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Test wrapper types")
    void testWrapperTypes() {
        // Given
        JavaFileObject entity = JavaFileObjects.forSourceString("com.example.WrapperEntity", """
            package com.example;
            
            public class WrapperEntity {
                private Long id;
                private Integer count;
                private Boolean active;
                private Double price;
                
                public WrapperEntity() {}
                
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                
                public Integer getCount() { return count; }
                public void setCount(Integer count) { this.count = count; }
                
                public Boolean isActive() { return active; }
                public void setActive(Boolean active) { this.active = active; }
                
                public Double getPrice() { return price; }
                public void setPrice(Double price) { this.price = price; }
            }
            """);

        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.WrapperRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(entity = WrapperEntity.class, tableName = "wrapper_entity")
            public class WrapperRepository {
                public List<WrapperEntity> findByCount(Integer count) { return null; }
                public boolean existsByActive(Boolean active) { return false; }
                public WrapperEntity save(WrapperEntity entity) { return null; }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(entity, repository);

        // Then
        assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("Test enum types")
    void testEnumTypes() {
        // Given
        JavaFileObject statusEnum = JavaFileObjects.forSourceString("com.example.Status", """
            package com.example;
            
            public enum Status {
                ACTIVE, INACTIVE, PENDING
            }
            """);

        JavaFileObject entity = JavaFileObjects.forSourceString("com.example.StatusEntity", """
            package com.example;
            
            public class StatusEntity {
                private Long id;
                private Status status;
                
                public StatusEntity() {}
                
                public Long getId() { return id; }
                public void setId(Long id) { this.id = id; }
                
                public Status getStatus() { return status; }
                public void setStatus(Status status) { this.status = status; }
            }
            """);

        JavaFileObject repository = JavaFileObjects.forSourceString("com.example.StatusRepository", """
            package com.example;
            
            import io.github.simplesqlgen.annotation.SqlGenerator;
            import java.util.List;
            
            @SqlGenerator(entity = StatusEntity.class, tableName = "status_entity")
            public class StatusRepository {
                public List<StatusEntity> findByStatus(Status status) { return null; }
                public long countByStatus(Status status) { return 0; }
                public StatusEntity save(StatusEntity entity) { return null; }
            }
            """);

        // When
        Compilation compilation = Compiler.javac()
                .withProcessors(new SqlProcessor())
                .compile(statusEnum, entity, repository);

        // Then
        assertThat(compilation).succeeded();
    }
}
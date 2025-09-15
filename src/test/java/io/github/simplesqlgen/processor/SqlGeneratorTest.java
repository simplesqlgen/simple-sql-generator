package io.github.simplesqlgen.processor;

import io.github.simplesqlgen.processor.sql.SqlGenerator;
import io.github.simplesqlgen.enums.NamingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for SqlGenerator class
 * Tests SQL generation logic, method name parsing, and condition parsing
 */
class SqlGeneratorTest {

    private SqlGenerator sqlGenerator;

    @BeforeEach
    void setUp() {
        sqlGenerator = new SqlGenerator();
    }

    @Test
    @DisplayName("Should map column names with SNAKE_CASE strategy")
    void testSnakeCaseColumnMapping() {
        // Given
        sqlGenerator.setNamingStrategy(NamingStrategy.SNAKE_CASE);

        // When & Then
        assertThat(mapColumnName("userName")).isEqualTo("user_name");
        assertThat(mapColumnName("firstName")).isEqualTo("first_name");
        assertThat(mapColumnName("id")).isEqualTo("id");
        assertThat(mapColumnName("createdAt")).isEqualTo("created_at");
    }

    @Test
    @DisplayName("Should map column names with CAMEL_CASE strategy")
    void testCamelCaseColumnMapping() {
        // Given
        sqlGenerator.setNamingStrategy(NamingStrategy.CAMEL_CASE);

        // When & Then
        assertThat(mapColumnName("userName")).isEqualTo("userName");
        assertThat(mapColumnName("firstName")).isEqualTo("firstName");
        assertThat(mapColumnName("id")).isEqualTo("id");
    }

    @Test
    @DisplayName("Should map column names with PASCAL_CASE strategy")
    void testPascalCaseColumnMapping() {
        // Given
        sqlGenerator.setNamingStrategy(NamingStrategy.PASCAL_CASE);

        // When & Then
        assertThat(mapColumnName("userName")).isEqualTo("UserName");
        assertThat(mapColumnName("firstName")).isEqualTo("FirstName");
        assertThat(mapColumnName("id")).isEqualTo("Id");
    }

    @Test
    @DisplayName("Should map column names with KEBAB_CASE strategy")
    void testKebabCaseColumnMapping() {
        // Given
        sqlGenerator.setNamingStrategy(NamingStrategy.KEBAB_CASE);

        // When & Then
        assertThat(mapColumnName("userName")).isEqualTo("user-name");
        assertThat(mapColumnName("firstName")).isEqualTo("first-name");
        assertThat(mapColumnName("id")).isEqualTo("id");
    }

    @Test
    @DisplayName("Should parse simple findBy method names")
    void testSimpleFindByParsing() {
        // Given
        String methodName = "findByName";

        // When
        SqlGenerator.QueryMethodInfo info = parseQueryMethodName(methodName);

        // Then
        assertThat(info.getOperation()).isEqualTo("find");
        assertThat(info.getFields()).containsExactly("name");
        assertThat(info.getOperators()).containsExactly("=");
    }

    @Test
    @DisplayName("Should parse complex findBy method names with And")
    void testComplexFindByWithAnd() {
        // Given
        String methodName = "findByNameAndAge";

        // When
        SqlGenerator.QueryMethodInfo info = parseQueryMethodName(methodName);

        // Then
        assertThat(info.getOperation()).isEqualTo("find");
        assertThat(info.getFields()).containsExactly("name", "age");
        assertThat(info.getOperators()).containsExactly("=", "=");
        assertThat(info.getLogicalOperators()).containsExactly("AND");
    }

    @Test
    @DisplayName("Should parse findBy method names with comparison operators")
    void testFindByWithComparisonOperators() {
        // Given
        String methodName = "findByAgeGreaterThan";

        // When
        SqlGenerator.QueryMethodInfo info = parseQueryMethodName(methodName);

        // Then
        assertThat(info.getOperation()).isEqualTo("find");
        assertThat(info.getFields()).containsExactly("age");
        assertThat(info.getOperators()).containsExactly(">");
    }

    @Test
    @DisplayName("Should parse countBy method names")
    void testCountByParsing() {
        // Given
        String methodName = "countByActive";

        // When
        SqlGenerator.QueryMethodInfo info = parseQueryMethodName(methodName);

        // Then
        assertThat(info.getOperation()).isEqualTo("count");
        assertThat(info.getFields()).containsExactly("active");
        assertThat(info.getOperators()).containsExactly("=");
    }

    @Test
    @DisplayName("Should parse existsBy method names")
    void testExistsByParsing() {
        // Given
        String methodName = "existsByEmail";

        // When
        SqlGenerator.QueryMethodInfo info = parseQueryMethodName(methodName);

        // Then
        assertThat(info.getOperation()).isEqualTo("exists");
        assertThat(info.getFields()).containsExactly("email");
        assertThat(info.getOperators()).containsExactly("=");
    }

    @Test
    @DisplayName("Should parse deleteBy method names")
    void testDeleteByParsing() {
        // Given
        String methodName = "deleteById";

        // When
        SqlGenerator.QueryMethodInfo info = parseQueryMethodName(methodName);

        // Then
        assertThat(info.getOperation()).isEqualTo("delete");
        assertThat(info.getFields()).containsExactly("id");
        assertThat(info.getOperators()).containsExactly("=");
    }

    // Helper methods to access private methods via reflection for testing
    private String mapColumnName(String fieldName) {
        try {
            var method = SqlGenerator.class.getDeclaredMethod("mapColumnName", String.class);
            method.setAccessible(true);
            return (String) method.invoke(sqlGenerator, fieldName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SqlGenerator.QueryMethodInfo parseQueryMethodName(String methodName) {
        try {
            var method = SqlGenerator.class.getDeclaredMethod("parseQueryMethodName", String.class);
            method.setAccessible(true);
            return (SqlGenerator.QueryMethodInfo) method.invoke(sqlGenerator, methodName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
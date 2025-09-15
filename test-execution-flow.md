# Test Execution Flow Documentation

## Overview

This document provides a comprehensive analysis of the execution flow for each test case in the Simple SQL Generator annotation processor project. The project transforms Java interfaces with annotations into SQL-executing implementations using compile-time code generation.

## Annotation Processing Architecture

### Core Components

1. **SqlProcessor** - Main annotation processor entry point
2. **ASTHelper** - AST manipulation and code generation utilities
3. **QueryExecutor** - Query execution code generation
4. **ParameterProcessor** - Parameter handling and validation
5. **SqlGenerator** - SQL query generation from method names
6. **Annotations**:
   - `@SqlGenerator` - Class-level configuration annotation
   - `@NativeQuery` - Method-level native SQL annotation
   - `@Param` - Parameter naming annotation

### Processing Lifecycle

```
Compilation Start
    ↓
SqlProcessor.init()
    ↓ 
Initialize Helper Classes (ASTHelper, QueryExecutor, ParameterProcessor, SqlGenerator)
    ↓
SqlProcessor.process() for each annotation round
    ↓
Process @SqlGenerator annotated classes
    ↓
For each method: Transform empty/stub methods into SQL implementations
    ↓
Generate @Autowired fields for dependency injection
    ↓
Compilation Complete
```

## Test Case Execution Flows

### 1. BasicTest.java

#### 1.1 testSimpleFindByParsing()
**Purpose**: Tests SQL generator method name parsing logic

**Execution Flow**:
```
Test Method
    ↓
SqlGenerator.parseQueryMethodName("findByName")
    ↓
SqlGenerator.parseCondition("Name") 
    ↓
Extract operation: "find"
Extract fields: ["name"]
Extract operators: ["="]
    ↓
Return QueryMethodInfo object
    ↓
Assert operation == "find" and fields contains "name"
```

**Key Classes**: `SqlGenerator`
**Key Methods**: `parseQueryMethodName()`, `parseCondition()`

#### 1.2 testCountByParsing()
**Purpose**: Tests COUNT query method parsing

**Execution Flow**:
```
Test Method
    ↓
SqlGenerator.parseQueryMethodName("countByActive")
    ↓
Extract operation: "count"
Extract fields: ["active"] 
Extract operators: ["="]
    ↓
Return QueryMethodInfo
    ↓
Assert operation == "count"
```

#### 1.3 testSimpleRepositoryCompilation()
**Purpose**: End-to-end compilation test with actual annotation processor

**Execution Flow**:
```
Test Setup: Create User entity + UserRepository with @SqlGenerator
    ↓
Compiler.javac().withProcessors(new SqlProcessor()).compile()
    ↓
SqlProcessor.init()
    ├── Initialize ASTHelper, QueryExecutor, ParameterProcessor, SqlGenerator
    └── Set up JavaC context and TreeMaker
    ↓
SqlProcessor.process()
    ↓
SqlProcessor.processAnnotatedClasses()
    ↓
For UserRepository class:
    ├── SqlProcessor.processSqlGeneratorClass()
    ├── SqlProcessor.createProcessingContext() - Extract entity=User.class, tableName="users"
    ├── SqlProcessor.analyzeEntity() - Extract User fields: [id, name]
    └── SqlProcessor.processClassWithAST()
        ↓
        ASTHelper.getTreePath() - Get AST tree path for class
        ↓
        ASTHelper.injectAutowiredFields() - Add @Autowired JdbcTemplate fields
        ↓
        SqlProcessor.transformClassMethods()
            ↓
            For findByName() method:
                ├── SqlProcessor.processMethodMember()
                ├── SqlProcessor.isEmptyMethod() - Check if method body is empty/stub
                ├── SqlProcessor.createImplementedMethod()
                └── SqlProcessor.processGeneratedSqlMethod()
                    ↓
                    SqlGenerator.createFindByImplementationWithValidation()
                        ├── SqlGenerator.parseQueryMethodName("findByName")
                        ├── SqlGenerator.generateAdvancedDynamicSQL() - Generate "SELECT * FROM users WHERE name = ?"
                        └── SqlGenerator.createQueryImplementation() - Generate JdbcTemplate query code
                            ↓
                            ASTHelper.createMethodCall() with JdbcTemplate.query()
                            ASTHelper.createBeanPropertyRowMapper() for User.class
                            ASTHelper.createReturnStatement()
    ↓
Assert compilation succeeded
```

**Key Decision Points**:
- Empty method detection triggers transformation
- Entity analysis determines available fields
- Method name parsing drives SQL generation

### 2. CodeGenerationTest.java

#### 2.1 testAutowiredFieldGeneration()
**Purpose**: Verify @Autowired field injection into repository classes

**Execution Flow**:
```
Test Setup: UserRepository with empty method bodies
    ↓
SqlProcessor.processClassWithAST()
    ↓
ASTHelper.injectAutowiredFields()
        ├── Check if JdbcTemplate fields already exist
        ├── Create @Autowired private JdbcTemplate jdbcTemplate field
        ├── Create @Autowired private NamedParameterJdbcTemplate namedParameterJdbcTemplate field  
        └── Add fields to class member list via reflection
    ↓
Transform methods: findByName(), findAll(), countByName()
    ├── findByName() → SELECT * FROM users WHERE name = ?
    ├── findAll() → SELECT * FROM users  
    └── countByName() → SELECT COUNT(*) FROM users WHERE name = ?
    ↓
Assert compilation succeeded (fields were injected)
```

#### 2.2 testNativeQueryOnlyMode()
**Purpose**: Test entity-less repositories using only @NativeQuery

**Execution Flow**:
```
Test Setup: @SqlGenerator(entity = void.class, nativeQueryOnly = true)
    ↓
SqlProcessor.createProcessingContext()
    ├── Detect entity = void.class → nativeQueryOnly = true
    └── Skip entity analysis
    ↓
For @NativeQuery methods:
    ├── getUserCount() with "SELECT COUNT(*) FROM users"
    └── findUsersByName() with "SELECT * FROM users WHERE name = ?"
    ↓
SqlProcessor.processNativeQueryMethod()
    ├── ParameterProcessor.analyzeMethodParameters() - Extract method parameters
    ├── SqlProcessor.createQueryExecution() - Determine if named/positional parameters
    └── QueryExecutor.createPositionalParameterQueryExecution()
        ├── Create JdbcTemplate.queryForObject() for scalar results
        └── Create JdbcTemplate.query() + ColumnMapRowMapper for List<Map>
    ↓
Assert compilation succeeded without entity dependency
```

#### 2.3 testShowProcessorOutput()
**Purpose**: Comprehensive test showing mixed generated + native queries

**Execution Flow**:
```
Test Setup: Mixed repository with both generated and @NativeQuery methods
    ↓
Process generated methods:
    ├── findByName() → SqlGenerator path
    ├── countByEmail() → SqlGenerator path  
    └── Generate standard CRUD SQL
    ↓
Process @NativeQuery methods:
    └── findByEmailAndName() → QueryExecutor path
        ├── ParameterProcessor.countPositionalParameters() - Count ? placeholders
        ├── ParameterProcessor.validatePositionalParameters() - Verify parameter count matches
        └── QueryExecutor.createPositionalParameterQueryExecution()
    ↓
Assert compilation succeeded with both paths working
```

### 3. NativeQueryFeatureTest.java

#### 3.1 testNamedParameters()
**Purpose**: Test named parameter (:param) processing

**Execution Flow**:
```
Test Setup: @NativeQuery with ":userName" parameter + @Param annotation
    ↓
SqlProcessor.processNativeQueryMethod()
    ↓
ParameterProcessor.analyzeMethodParameters()
    ├── Extract @Param("userName") annotation
    ├── Set parameterType = NAMED
    └── Map parameter name to "userName"
    ↓
SqlProcessor.hasNamedParameters() - Detect ":" in SQL
    ↓
SqlProcessor.createNamedParameterExecution()
    ├── ParameterProcessor.extractNamedParameters() - Find :userName in SQL
    ├── ParameterProcessor.validateParameterMapping() - Verify :userName maps to method param
    └── QueryExecutor.createNamedParameterQueryExecution()
        ├── Create NamedParameterJdbcTemplate access
        ├── QueryExecutor.createParameterSourceCreation() - Create MapSqlParameterSource
        ├── Add paramSource.addValue("userName", userName) calls
        └── Generate namedJdbcTemplate.query() call
    ↓
Assert compilation succeeded
```

#### 3.2 testPositionalParameters()
**Purpose**: Test positional parameter (?) processing

**Execution Flow**:
```
Test Setup: @NativeQuery with "?" placeholders
    ↓
SqlProcessor.processNativeQueryMethod()
    ↓
ParameterProcessor.countPositionalParameters() - Count ? in SQL
    ↓
ParameterProcessor.validatePositionalParameters() - Verify ? count == param count
    ↓
QueryExecutor.createPositionalParameterQueryExecution()
    ├── Create JdbcTemplate access
    ├── QueryExecutor.createParameterArray() - Create Object[] from method params
    └── Generate jdbcTemplate.query(sql, rowMapper, paramArray) call
    ↓
Assert compilation succeeded
```

#### 3.3 testVariousReturnTypes()
**Purpose**: Test different return type handling (primitives, objects, void)

**Execution Flow**:
```
For each return type:
    ↓
QueryExecutor.createAutoMappingQuery()
    ├── long return → Use queryForObject() + Long.class
    ├── String return → Use queryForObject() + String.class  
    ├── int return → Use queryForObject() + Integer.class
    ├── void return → Use update() + createExpressionStatement()
    └── boolean return → Use queryForObject() + Boolean.class
    ↓
QueryExecutor.createTypeCastExpression() - Add type casting for non-primitives
    ↓
ASTHelper.createReturnStatement() or ASTHelper.createExpressionStatement()
    ↓
Assert compilation succeeded for all return types
```

### 4. SqlGeneratorTest.java

#### 4.1 Naming Strategy Tests
**Purpose**: Test column name mapping with different naming strategies

**Execution Flow**:
```
For each NamingStrategy:
    ↓
SqlGenerator.setNamingStrategy(strategy)
    ↓
SqlGenerator.mapColumnName(fieldName)
    ├── SNAKE_CASE: "userName" → "user_name"
    ├── CAMEL_CASE: "userName" → "userName"  
    ├── PASCAL_CASE: "userName" → "UserName"
    └── KEBAB_CASE: "userName" → "user-name"
    ↓
Assert mapped name matches expected pattern
```

#### 4.2 Method Name Parsing Tests
**Purpose**: Test query method name parsing logic

**Execution Flow**:
```
Test Method Name → SqlGenerator.parseQueryMethodName()
    ↓
SqlGenerator.parseCondition() - Parse condition part after "By"
    ├── Split on "And"/"Or" → Extract logical operators
    ├── Check for comparison operators (GreaterThan, Like, etc.)
    ├── Extract field names and convert to camelCase
    └── Map operators to SQL equivalents
    ↓
Return QueryMethodInfo with:
    ├── operation (find/count/delete/exists)
    ├── fields list
    ├── operators list  
    └── logicalOperators list
    ↓
Assert parsed information matches expected
```

### 5. ErrorHandlingTest.java

#### 5.1 testInvalidAnnotationConfiguration()
**Purpose**: Test processor resilience with invalid configurations

**Execution Flow**:
```
Test Setup: @SqlGenerator without entity specified
    ↓
SqlProcessor.createProcessingContext()
    ├── getEntityType() returns null or void
    ├── Set nativeQueryOnly = true automatically
    └── Create empty EntityInfo
    ↓
SqlProcessor.validateEntityInfo() - Skip validation in native-only mode
    ↓
Method processing continues without entity dependency
    ↓
Assert compilation succeeded (graceful degradation)
```

#### 5.2 testNonExistentFieldInQuery()
**Purpose**: Test handling of method names with invalid field references

**Execution Flow**:
```
Test Setup: findByNonExistentField() method where "nonExistentField" not in entity
    ↓
SqlGenerator.createFindByImplementationWithValidation()
    ↓
SqlGenerator.isValidEntityField() - Check if field exists in entity
    ├── Field validation fails
    ├── Log warning but continue processing
    └── Generate SQL anyway: "SELECT * FROM users WHERE non_existent_field = ?"
    ↓
SqlGenerator.createQueryImplementation() - Generate JdbcTemplate code
    ↓
Assert compilation succeeded (best-effort processing)
```

### 6. DataTypeSupportTest.java

#### 6.1 testPrimitiveDataTypes()
**Purpose**: Test primitive type handling in entities and parameters

**Execution Flow**:
```
Test Setup: Entity with int, long, boolean, double, String fields
    ↓
SqlProcessor.analyzeEntity() - Extract field names and types
    ↓
For save() method:
    ├── SqlGenerator.createSaveImplementationWithEntity()
    ├── Generate INSERT SQL with all fields
    ├── SqlGenerator.getBooleanAwareGetter() - Use isXxx() for boolean fields
    └── Create entity.getField() calls for parameter array
    ↓
For query methods:
    ├── Generate WHERE clauses with appropriate field types
    └── Handle primitive return types without casting
    ↓
Assert compilation succeeded with primitive type support
```

#### 6.2 testWrapperTypes()
**Purpose**: Test wrapper type handling (Long, Integer, Boolean, etc.)

**Execution Flow**:
```
Similar to primitive test but:
    ↓
QueryExecutor.createTypeCastExpression() - Add casting for wrapper types
    ↓
QueryExecutor.createPrimitiveNullSafeExpression() - Handle null values for primitives
    ├── Generate: result != null ? result.longValue() : 0L
    └── Use appropriate default values for each primitive type
    ↓
Assert compilation succeeded with null-safe wrapper handling
```

### 7. ComprehensiveTest.java

#### 7.1 testBasicCrudOperations()
**Purpose**: Test complete CRUD operation generation

**Execution Flow**:
```
Test Setup: Repository with findBy, save, count, delete methods
    ↓
For each CRUD operation:
    ├── findByName() → SELECT * FROM table WHERE name = ?
    ├── save() → INSERT INTO table (...) VALUES (...)
    ├── countByActive() → SELECT COUNT(*) FROM table WHERE active = ?
    └── deleteById() → DELETE FROM table WHERE id = ?
    ↓
Each operation triggers appropriate SqlGenerator method:
    ├── createFindByImplementationWithValidation()
    ├── createSaveImplementationWithEntity()  
    ├── createCountByImplementationWithValidation()
    └── createDeleteByImplementationWithValidation()
    ↓
Assert all CRUD operations compile successfully
```

#### 7.2 testMixedRepository()
**Purpose**: Test combination of generated and native queries

**Execution Flow**:
```
Process generated methods via SqlGenerator path
    ↓
Process @NativeQuery methods via QueryExecutor path
    ↓
Both paths inject @Autowired fields (idempotent)
    ↓
Method transformation handles both:
    ├── Empty methods → Generated SQL implementation
    └── @NativeQuery methods → Native SQL implementation
    ↓
Assert mixed approach works correctly
```

## Key Decision Points and Code Paths

### 1. Entity vs Native-Only Mode
```
SqlProcessor.createProcessingContext()
    ↓
if (annotation.entity() == void.class || annotation.nativeQueryOnly()) {
    → Native-only mode: Skip entity analysis
} else {
    → Entity mode: Analyze entity fields and generate CRUD
}
```

### 2. Method Transformation Trigger
```
SqlProcessor.processMethodMember()
    ↓
if (isEmptyMethod() || hasNativeQueryAnnotation() || isGeneratedMethodName()) {
    → Transform method body
} else {
    → Leave method unchanged
}
```

### 3. Parameter Type Detection
```
SqlProcessor.createQueryExecution()
    ↓
if (hasNamedParameters(sql)) {
    → Use NamedParameterJdbcTemplate + MapSqlParameterSource
} else {
    → Use JdbcTemplate + Object[] parameters
}
```

### 4. Return Type Handling
```
QueryExecutor.createAutoMappingQuery()
    ↓
if (returnType.startsWith("java.util.List")) {
    → Use jdbcTemplate.query() + RowMapper
} else if (isSimpleType(returnType)) {
    → Use jdbcTemplate.queryForObject() + Class literal
} else {
    → Use jdbcTemplate.queryForObject() + BeanPropertyRowMapper
}
```

### 5. Error Recovery Patterns
- Field validation failures → Continue with best-effort SQL generation
- AST manipulation failures → Return original method unchanged  
- Type casting failures → Return expression without cast
- Import addition failures → Continue without imports

## Performance Considerations

1. **Reflection Usage**: Heavy use of reflection for AST manipulation - cached where possible
2. **AST Traversal**: Single-pass AST transformation to minimize compilation overhead
3. **Helper Initialization**: Helpers initialized once per processor instance
4. **Method Processing**: Only transform empty/annotated methods, skip others

## Testing Coverage

The test suite covers:
- ✅ Basic functionality (method parsing, SQL generation)
- ✅ Code generation and AST manipulation  
- ✅ Named and positional parameter handling
- ✅ Various return types and data types
- ✅ Error conditions and edge cases
- ✅ Complex entities and method patterns
- ✅ Mixed generated/native query repositories
- ✅ Different naming strategies and configurations

This comprehensive test execution flow demonstrates the robustness and flexibility of the annotation processor architecture.
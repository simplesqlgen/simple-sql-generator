# 테스트 실행 흐름 문서

## 개요

이 문서는 Simple SQL Generator 어노테이션 프로세서 프로젝트의 각 테스트 케이스 실행 흐름에 대한 포괄적인 분석을 제공합니다. 이 프로젝트는 어노테이션이 달린 Java 인터페이스를 컴파일 타임 코드 생성을 통해 SQL을 실행하는 구현체로 변환합니다.

## 어노테이션 처리 아키텍처

### 핵심 구성 요소

1. **SqlProcessor** - 메인 어노테이션 프로세서 진입점
2. **ASTHelper** - AST(추상 구문 트리) 조작 및 코드 생성 유틸리티
3. **QueryExecutor** - 쿼리 실행 코드 생성
4. **ParameterProcessor** - 매개변수 처리 및 검증
5. **SqlGenerator** - 메서드명으로부터 SQL 쿼리 생성
6. **어노테이션들**:
   - `@SqlGenerator` - 클래스 레벨 설정 어노테이션
   - `@NativeQuery` - 메서드 레벨 네이티브 SQL 어노테이션
   - `@Param` - 매개변수 이름 지정 어노테이션

### 처리 생명주기

```
컴파일 시작
    ↓
SqlProcessor.init()
    ↓ 
헬퍼 클래스 초기화 (ASTHelper, QueryExecutor, ParameterProcessor, SqlGenerator)
    ↓
각 어노테이션 라운드에 대해 SqlProcessor.process() 실행
    ↓
@SqlGenerator 어노테이션이 달린 클래스 처리
    ↓
각 메서드에 대해: 빈/스텁 메서드를 SQL 구현체로 변환
    ↓
의존성 주입을 위한 @Autowired 필드 생성
    ↓
컴파일 완료
```

## 테스트 케이스 실행 흐름

### 1. BasicTest.java

#### 1.1 testSimpleFindByParsing()
**목적**: SQL 생성기 메서드명 파싱 로직 테스트

**실행 흐름**:
```
테스트 메서드
    ↓
SqlGenerator.parseQueryMethodName("findByName")
    ↓
SqlGenerator.parseCondition("Name") 
    ↓
연산 추출: "find"
필드 추출: ["name"]
연산자 추출: ["="]
    ↓
QueryMethodInfo 객체 반환
    ↓
연산이 "find"이고 필드에 "name"이 포함되어 있는지 검증
```

**핵심 클래스**: `SqlGenerator`
**핵심 메서드**: `parseQueryMethodName()`, `parseCondition()`

#### 1.2 testCountByParsing()
**목적**: COUNT 쿼리 메서드 파싱 테스트

**실행 흐름**:
```
테스트 메서드
    ↓
SqlGenerator.parseQueryMethodName("countByActive")
    ↓
연산 추출: "count"
필드 추출: ["active"] 
연산자 추출: ["="]
    ↓
QueryMethodInfo 반환
    ↓
연산이 "count"인지 검증
```

#### 1.3 testSimpleRepositoryCompilation()
**목적**: 실제 어노테이션 프로세서를 사용한 종단간 컴파일 테스트

**실행 흐름**:
```
테스트 설정: User 엔티티 + @SqlGenerator가 있는 UserRepository 생성
    ↓
Compiler.javac().withProcessors(new SqlProcessor()).compile()
    ↓
SqlProcessor.init()
    ├── ASTHelper, QueryExecutor, ParameterProcessor, SqlGenerator 초기화
    └── JavaC 컨텍스트 및 TreeMaker 설정
    ↓
SqlProcessor.process()
    ↓
SqlProcessor.processAnnotatedClasses()
    ↓
UserRepository 클래스에 대해:
    ├── SqlProcessor.processSqlGeneratorClass()
    ├── SqlProcessor.createProcessingContext() - entity=User.class, tableName="users" 추출
    ├── SqlProcessor.analyzeEntity() - User 필드 추출: [id, name]
    └── SqlProcessor.processClassWithAST()
        ↓
        ASTHelper.getTreePath() - 클래스에 대한 AST 트리 경로 획득
        ↓
        ASTHelper.injectAutowiredFields() - @Autowired JdbcTemplate 필드 추가
        ↓
        SqlProcessor.transformClassMethods()
            ↓
            findByName() 메서드에 대해:
                ├── SqlProcessor.processMethodMember()
                ├── SqlProcessor.isEmptyMethod() - 메서드 본문이 비어있거나 스텁인지 확인
                ├── SqlProcessor.createImplementedMethod()
                └── SqlProcessor.processGeneratedSqlMethod()
                    ↓
                    SqlGenerator.createFindByImplementationWithValidation()
                        ├── SqlGenerator.parseQueryMethodName("findByName")
                        ├── SqlGenerator.generateAdvancedDynamicSQL() - "SELECT * FROM users WHERE name = ?" 생성
                        └── SqlGenerator.createQueryImplementation() - JdbcTemplate 쿼리 코드 생성
                            ↓
                            ASTHelper.createMethodCall() JdbcTemplate.query()와 함께
                            ASTHelper.createBeanPropertyRowMapper() User.class에 대해
                            ASTHelper.createReturnStatement()
    ↓
컴파일 성공 검증
```

**핵심 결정 지점들**:
- 빈 메서드 감지가 변환을 트리거
- 엔티티 분석이 사용 가능한 필드 결정
- 메서드명 파싱이 SQL 생성을 주도

### 2. CodeGenerationTest.java

#### 2.1 testAutowiredFieldGeneration()
**목적**: 레포지토리 클래스에 @Autowired 필드 주입 검증

**실행 흐름**:
```
테스트 설정: 빈 메서드 본문을 가진 UserRepository
    ↓
SqlProcessor.processClassWithAST()
    ↓
ASTHelper.injectAutowiredFields()
        ├── JdbcTemplate 필드가 이미 존재하는지 확인
        ├── @Autowired private JdbcTemplate jdbcTemplate 필드 생성
        ├── @Autowired private NamedParameterJdbcTemplate namedParameterJdbcTemplate 필드 생성
        └── 리플렉션을 통해 클래스 멤버 목록에 필드 추가
    ↓
메서드 변환: findByName(), findAll(), countByName()
    ├── findByName() → SELECT * FROM users WHERE name = ?
    ├── findAll() → SELECT * FROM users  
    └── countByName() → SELECT COUNT(*) FROM users WHERE name = ?
    ↓
컴파일 성공 검증 (필드가 주입됨)
```

#### 2.2 testNativeQueryOnlyMode()
**목적**: @NativeQuery만 사용하는 엔티티 없는 레포지토리 테스트

**실행 흐름**:
```
테스트 설정: @SqlGenerator(entity = void.class, nativeQueryOnly = true)
    ↓
SqlProcessor.createProcessingContext()
    ├── entity = void.class 감지 → nativeQueryOnly = true
    └── 엔티티 분석 건너뛰기
    ↓
@NativeQuery 메서드에 대해:
    ├── getUserCount() "SELECT COUNT(*) FROM users"와 함께
    └── findUsersByName() "SELECT * FROM users WHERE name = ?"와 함께
    ↓
SqlProcessor.processNativeQueryMethod()
    ├── ParameterProcessor.analyzeMethodParameters() - 메서드 매개변수 추출
    ├── SqlProcessor.createQueryExecution() - 명명된/위치적 매개변수 결정
    └── QueryExecutor.createPositionalParameterQueryExecution()
        ├── 스칼라 결과에 대해 JdbcTemplate.queryForObject() 생성
        └── List<Map>에 대해 JdbcTemplate.query() + ColumnMapRowMapper 생성
    ↓
엔티티 의존성 없이 컴파일 성공 검증
```

#### 2.3 testShowProcessorOutput()
**목적**: 생성된 쿼리와 네이티브 쿼리가 혼합된 포괄적인 테스트

**실행 흐름**:
```
테스트 설정: 생성된 메서드와 @NativeQuery 메서드가 모두 있는 혼합 레포지토리
    ↓
생성된 메서드 처리:
    ├── findByName() → SqlGenerator 경로
    ├── countByEmail() → SqlGenerator 경로  
    └── 표준 CRUD SQL 생성
    ↓
@NativeQuery 메서드 처리:
    └── findByEmailAndName() → QueryExecutor 경로
        ├── ParameterProcessor.countPositionalParameters() - ? 플레이스홀더 개수 계산
        ├── ParameterProcessor.validatePositionalParameters() - 매개변수 개수가 일치하는지 검증
        └── QueryExecutor.createPositionalParameterQueryExecution()
    ↓
두 경로 모두 작동하는 컴파일 성공 검증
```

### 3. NativeQueryFeatureTest.java

#### 3.1 testNamedParameters()
**목적**: 명명된 매개변수 (:param) 처리 테스트

**실행 흐름**:
```
테스트 설정: ":userName" 매개변수와 @Param 어노테이션이 있는 @NativeQuery
    ↓
SqlProcessor.processNativeQueryMethod()
    ↓
ParameterProcessor.analyzeMethodParameters()
    ├── @Param("userName") 어노테이션 추출
    ├── parameterType = NAMED 설정
    └── 매개변수 이름을 "userName"으로 매핑
    ↓
SqlProcessor.hasNamedParameters() - SQL에서 ":" 감지
    ↓
SqlProcessor.createNamedParameterExecution()
    ├── ParameterProcessor.extractNamedParameters() - SQL에서 :userName 찾기
    ├── ParameterProcessor.validateParameterMapping() - :userName이 메서드 매개변수에 매핑되는지 검증
    └── QueryExecutor.createNamedParameterQueryExecution()
        ├── NamedParameterJdbcTemplate 접근 생성
        ├── QueryExecutor.createParameterSourceCreation() - MapSqlParameterSource 생성
        ├── paramSource.addValue("userName", userName) 호출 추가
        └── namedJdbcTemplate.query() 호출 생성
    ↓
컴파일 성공 검증
```

#### 3.2 testPositionalParameters()
**목적**: 위치적 매개변수 (?) 처리 테스트

**실행 흐름**:
```
테스트 설정: "?" 플레이스홀더가 있는 @NativeQuery
    ↓
SqlProcessor.processNativeQueryMethod()
    ↓
ParameterProcessor.countPositionalParameters() - SQL에서 ? 개수 계산
    ↓
ParameterProcessor.validatePositionalParameters() - ? 개수 == 매개변수 개수 검증
    ↓
QueryExecutor.createPositionalParameterQueryExecution()
    ├── JdbcTemplate 접근 생성
    ├── QueryExecutor.createParameterArray() - 메서드 매개변수로부터 Object[] 생성
    └── jdbcTemplate.query(sql, rowMapper, paramArray) 호출 생성
    ↓
컴파일 성공 검증
```

#### 3.3 testVariousReturnTypes()
**목적**: 다양한 반환 타입 처리 테스트 (원시 타입, 객체, void)

**실행 흐름**:
```
각 반환 타입에 대해:
    ↓
QueryExecutor.createAutoMappingQuery()
    ├── long 반환 → queryForObject() + Long.class 사용
    ├── String 반환 → queryForObject() + String.class 사용  
    ├── int 반환 → queryForObject() + Integer.class 사용
    ├── void 반환 → update() + createExpressionStatement() 사용
    └── boolean 반환 → queryForObject() + Boolean.class 사용
    ↓
QueryExecutor.createTypeCastExpression() - 비원시 타입에 대한 타입 캐스팅 추가
    ↓
ASTHelper.createReturnStatement() 또는 ASTHelper.createExpressionStatement()
    ↓
모든 반환 타입에 대해 컴파일 성공 검증
```

### 4. SqlGeneratorTest.java

#### 4.1 명명 전략 테스트들
**목적**: 다양한 명명 전략으로 컬럼명 매핑 테스트

**실행 흐름**:
```
각 NamingStrategy에 대해:
    ↓
SqlGenerator.setNamingStrategy(strategy)
    ↓
SqlGenerator.mapColumnName(fieldName)
    ├── SNAKE_CASE: "userName" → "user_name"
    ├── CAMEL_CASE: "userName" → "userName"  
    ├── PASCAL_CASE: "userName" → "UserName"
    └── KEBAB_CASE: "userName" → "user-name"
    ↓
매핑된 이름이 예상 패턴과 일치하는지 검증
```

#### 4.2 메서드명 파싱 테스트들
**목적**: 쿼리 메서드명 파싱 로직 테스트

**실행 흐름**:
```
테스트 메서드명 → SqlGenerator.parseQueryMethodName()
    ↓
SqlGenerator.parseCondition() - "By" 뒤의 조건 부분 파싱
    ├── "And"/"Or"로 분할 → 논리 연산자 추출
    ├── 비교 연산자 확인 (GreaterThan, Like 등)
    ├── 필드명 추출하고 camelCase로 변환
    └── 연산자를 SQL 동등물로 매핑
    ↓
다음을 포함한 QueryMethodInfo 반환:
    ├── operation (find/count/delete/exists)
    ├── fields 목록
    ├── operators 목록  
    └── logicalOperators 목록
    ↓
파싱된 정보가 예상과 일치하는지 검증
```

### 5. ErrorHandlingTest.java

#### 5.1 testInvalidAnnotationConfiguration()
**목적**: 잘못된 설정에 대한 프로세서 복원력 테스트

**실행 흐름**:
```
테스트 설정: 엔티티가 지정되지 않은 @SqlGenerator
    ↓
SqlProcessor.createProcessingContext()
    ├── getEntityType()이 null 또는 void 반환
    ├── nativeQueryOnly = true 자동 설정
    └── 빈 EntityInfo 생성
    ↓
SqlProcessor.validateEntityInfo() - 네이티브 전용 모드에서 검증 건너뛰기
    ↓
엔티티 의존성 없이 메서드 처리 계속
    ↓
컴파일 성공 검증 (우아한 성능 저하)
```

#### 5.2 testNonExistentFieldInQuery()
**목적**: 유효하지 않은 필드 참조가 있는 메서드명 처리 테스트

**실행 흐름**:
```
테스트 설정: "nonExistentField"가 엔티티에 없는 findByNonExistentField() 메서드
    ↓
SqlGenerator.createFindByImplementationWithValidation()
    ↓
SqlGenerator.isValidEntityField() - 필드가 엔티티에 존재하는지 확인
    ├── 필드 검증 실패
    ├── 경고 로그 기록하지만 처리 계속
    └── 어쨌든 SQL 생성: "SELECT * FROM users WHERE non_existent_field = ?"
    ↓
SqlGenerator.createQueryImplementation() - JdbcTemplate 코드 생성
    ↓
컴파일 성공 검증 (최선 노력 처리)
```

### 6. DataTypeSupportTest.java

#### 6.1 testPrimitiveDataTypes()
**목적**: 엔티티와 매개변수에서 원시 타입 처리 테스트

**실행 흐름**:
```
테스트 설정: int, long, boolean, double, String 필드를 가진 엔티티
    ↓
SqlProcessor.analyzeEntity() - 필드명과 타입 추출
    ↓
save() 메서드에 대해:
    ├── SqlGenerator.createSaveImplementationWithEntity()
    ├── 모든 필드를 가진 INSERT SQL 생성
    ├── SqlGenerator.getBooleanAwareGetter() - boolean 필드에 대해 isXxx() 사용
    └── 매개변수 배열에 대해 entity.getField() 호출 생성
    ↓
쿼리 메서드에 대해:
    ├── 적절한 필드 타입을 가진 WHERE 절 생성
    └── 캐스팅 없이 원시 반환 타입 처리
    ↓
원시 타입 지원으로 컴파일 성공 검증
```

#### 6.2 testWrapperTypes()
**목적**: 래퍼 타입 처리 테스트 (Long, Integer, Boolean 등)

**실행 흐름**:
```
원시 테스트와 유사하지만:
    ↓
QueryExecutor.createTypeCastExpression() - 래퍼 타입에 대한 캐스팅 추가
    ↓
QueryExecutor.createPrimitiveNullSafeExpression() - 원시 타입에 대한 null 값 처리
    ├── 생성: result != null ? result.longValue() : 0L
    └── 각 원시 타입에 대해 적절한 기본값 사용
    ↓
null 안전 래퍼 처리로 컴파일 성공 검증
```

### 7. ComprehensiveTest.java

#### 7.1 testBasicCrudOperations()
**목적**: 완전한 CRUD 연산 생성 테스트

**실행 흐름**:
```
테스트 설정: findBy, save, count, delete 메서드를 가진 레포지토리
    ↓
각 CRUD 연산에 대해:
    ├── findByName() → SELECT * FROM table WHERE name = ?
    ├── save() → INSERT INTO table (...) VALUES (...)
    ├── countByActive() → SELECT COUNT(*) FROM table WHERE active = ?
    └── deleteById() → DELETE FROM table WHERE id = ?
    ↓
각 연산은 적절한 SqlGenerator 메서드를 트리거:
    ├── createFindByImplementationWithValidation()
    ├── createSaveImplementationWithEntity()  
    ├── createCountByImplementationWithValidation()
    └── createDeleteByImplementationWithValidation()
    ↓
모든 CRUD 연산이 성공적으로 컴파일되는지 검증
```

#### 7.2 testMixedRepository()
**목적**: 생성된 쿼리와 네이티브 쿼리의 조합 테스트

**실행 흐름**:
```
SqlGenerator 경로를 통해 생성된 메서드 처리
    ↓
QueryExecutor 경로를 통해 @NativeQuery 메서드 처리
    ↓
두 경로 모두 @Autowired 필드 주입 (멱등)
    ↓
메서드 변환이 둘 다 처리:
    ├── 빈 메서드 → 생성된 SQL 구현
    └── @NativeQuery 메서드 → 네이티브 SQL 구현
    ↓
혼합 접근법이 올바르게 작동하는지 검증
```

## 핵심 결정 지점과 코드 경로

### 1. 엔티티 vs 네이티브 전용 모드
```
SqlProcessor.createProcessingContext()
    ↓
if (annotation.entity() == void.class || annotation.nativeQueryOnly()) {
    → 네이티브 전용 모드: 엔티티 분석 건너뛰기
} else {
    → 엔티티 모드: 엔티티 필드 분석하고 CRUD 생성
}
```

### 2. 메서드 변환 트리거
```
SqlProcessor.processMethodMember()
    ↓
if (isEmptyMethod() || hasNativeQueryAnnotation() || isGeneratedMethodName()) {
    → 메서드 본문 변환
} else {
    → 메서드 변경하지 않고 유지
}
```

### 3. 매개변수 타입 감지
```
SqlProcessor.createQueryExecution()
    ↓
if (hasNamedParameters(sql)) {
    → NamedParameterJdbcTemplate + MapSqlParameterSource 사용
} else {
    → JdbcTemplate + Object[] 매개변수 사용
}
```

### 4. 반환 타입 처리
```
QueryExecutor.createAutoMappingQuery()
    ↓
if (returnType.startsWith("java.util.List")) {
    → jdbcTemplate.query() + RowMapper 사용
} else if (isSimpleType(returnType)) {
    → jdbcTemplate.queryForObject() + 클래스 리터럴 사용
} else {
    → jdbcTemplate.queryForObject() + BeanPropertyRowMapper 사용
}
```

### 5. 오류 복구 패턴
- 필드 검증 실패 → 최선 노력 SQL 생성으로 계속
- AST 조작 실패 → 원본 메서드 변경하지 않고 반환  
- 타입 캐스팅 실패 → 캐스트 없이 표현식 반환
- import 추가 실패 → import 없이 계속

## 성능 고려사항

1. **리플렉션 사용**: AST 조작을 위한 리플렉션 많이 사용 - 가능한 경우 캐시
2. **AST 순회**: 컴파일 오버헤드를 최소화하기 위한 단일 패스 AST 변환
3. **헬퍼 초기화**: 프로세서 인스턴스당 한 번 헬퍼 초기화
4. **메서드 처리**: 빈/어노테이션 메서드만 변환, 다른 것들은 건너뛰기

## 테스트 커버리지

테스트 스위트는 다음을 커버합니다:
- ✅ 기본 기능 (메서드 파싱, SQL 생성)
- ✅ 코드 생성 및 AST 조작  
- ✅ 명명된 및 위치적 매개변수 처리
- ✅ 다양한 반환 타입 및 데이터 타입
- ✅ 오류 조건 및 경계 케이스
- ✅ 복잡한 엔티티 및 메서드 패턴
- ✅ 생성된/네이티브 쿼리 혼합 레포지토리
- ✅ 다양한 명명 전략 및 설정

이 포괄적인 테스트 실행 흐름은 어노테이션 프로세서 아키텍처의 견고성과 유연성을 보여줍니다.
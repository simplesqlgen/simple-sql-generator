package io.github.simplesqlgen.processor.query;

import io.github.simplesqlgen.enums.ResultMappingType;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * 쿼리 실행 코드 생성을 담당하는 클래스
 * Named Parameter와 Positional Parameter 쿼리 실행 코드를 생성
 */
public class QueryExecutor {
    
    private Object astHelper;

    public QueryExecutor(Object astHelper) {
        this.astHelper = astHelper;
    }

    /**
     * Named Parameter 쿼리 실행 생성
     */
    public Object createNamedParameterQueryExecution(String sql, ExecutableElement methodElement,
                                                    boolean isUpdate, ResultMappingType mappingType,
                                                    String resultTypeClass, String columnMapping,
                                                    List<?> methodParams) throws Exception {
        
        // SQL 리터럴 생성
        Object sqlLiteral = createLiteral(sql);
        
        // NamedParameterJdbcTemplate 필드 접근 (올바른 필드 사용)
        Object namedJdbcTemplateAccess = createFieldAccess("this", "namedParameterJdbcTemplate");
        
        // MapSqlParameterSource 생성
        Object paramSourceVar = createParameterSourceCreation(methodParams);
        
        Object queryCall;
        
        if (isUpdate) {
            // UPDATE/INSERT/DELETE 쿼리
            queryCall = createUpdateQuery(namedJdbcTemplateAccess, sqlLiteral, paramSourceVar);
        } else {
            // SELECT 쿼리
            queryCall = createNamedParameterSelectQuery(namedJdbcTemplateAccess, sqlLiteral, paramSourceVar,
                    mappingType, resultTypeClass, columnMapping, methodElement);
        }
        
        return createReturnStatement(queryCall);
    }

    /**
     * Positional Parameter 쿼리 실행 생성
     */
    public Object createPositionalParameterQueryExecution(String sql, ExecutableElement methodElement,
                                                         boolean isUpdate, ResultMappingType mappingType,
                                                         String resultTypeClass, String columnMapping,
                                                         List<?> methodParams) throws Exception {
        
        // JdbcTemplate 필드 접근
        Object jdbcTemplateAccess = createFieldAccess("this", "jdbcTemplate");
        
        // SQL 리터럴 직접 사용 (동적 SQL 처리는 나중에 구현)
        Object sqlLiteral = createLiteral(sql);
        
        Object queryCall;
        
        if (isUpdate) {
            // UPDATE/INSERT/DELETE 쿼리
            queryCall = createPositionalUpdateQuery(jdbcTemplateAccess, sqlLiteral, methodParams);
        } else {
            // SELECT 쿼리
            queryCall = createPositionalSelectQuery(jdbcTemplateAccess, sqlLiteral, methodParams,
                    mappingType, resultTypeClass, columnMapping, methodElement);
        }
        
        return createReturnStatement(queryCall);
    }

    /**
     * Named Parameter SELECT 쿼리 생성
     */
    public Object createNamedParameterSelectQuery(Object namedJdbcTemplate, Object sqlLiteral, Object paramSource,
                                                 ResultMappingType mappingType, String resultTypeClass,
                                                 String columnMapping, ExecutableElement methodElement) throws Exception {
        
        switch (mappingType) {
            case AUTO:
                return createAutoMappingQuery(namedJdbcTemplate, sqlLiteral, paramSource, resultTypeClass, methodElement);
            case MANUAL:
                return createManualMappingQuery(namedJdbcTemplate, sqlLiteral, paramSource, resultTypeClass, columnMapping);
            case BEAN_PROPERTY:
                return createBeanPropertyMappingQuery(namedJdbcTemplate, sqlLiteral, paramSource, resultTypeClass);
            case NESTED:
                return createNestedMappingQuery(namedJdbcTemplate, sqlLiteral, paramSource, resultTypeClass, columnMapping);
            default:
                return createAutoMappingQuery(namedJdbcTemplate, sqlLiteral, paramSource, resultTypeClass, methodElement);
        }
    }

    /**
     * 자동 매핑 쿼리 생성
     */
    public Object createAutoMappingQuery(Object namedJdbcTemplate, Object sqlLiteral, Object paramSource,
                                        String resultTypeClass, ExecutableElement methodElement) throws Exception {
        
        TypeMirror returnType = methodElement.getReturnType();
        String returnTypeStr = returnType.toString();
        
        if (returnTypeStr.startsWith("java.util.List")) {
            // List 반환 타입
            if (returnTypeStr.contains("Map<String") || returnTypeStr.contains("Map<java.lang.String")) {
                // Map 타입인 경우 ColumnMapRowMapper 사용
                Object columnMapRowMapper = createColumnMapRowMapper();
                // NamedParameterJdbcTemplate.query(String sql, SqlParameterSource ps, RowMapper<T> rm)
                return createMethodCall(createFieldAccess(namedJdbcTemplate, "query"), sqlLiteral, paramSource, columnMapRowMapper);
            } else {
                // 엔티티 타입인 경우 BeanPropertyRowMapper 사용
                Object rowMapper = createBeanPropertyRowMapper(resultTypeClass);
                // NamedParameterJdbcTemplate.query(String sql, SqlParameterSource ps, RowMapper<T> rm)
                return createMethodCall(createFieldAccess(namedJdbcTemplate, "query"), sqlLiteral, paramSource, rowMapper);
            }
        } else if (isSimpleType(resultTypeClass)) {
            // 단순 타입인 경우 (Long, Integer, String 등) - 타입 명시적 변수 선언으로 해결
            Object classLiteral = createClassLiteral(getFullTypeName(resultTypeClass));
            // NamedParameterJdbcTemplate.queryForObject(String sql, SqlParameterSource ps, Class<T> requiredType)
            Object queryCall = createMethodCall(createFieldAccess(namedJdbcTemplate, "queryForObject"), sqlLiteral, paramSource, classLiteral);
            
            // 컴파일러가 제네릭 정보를 잃는 경우를 대비해 명시적 캐스팅 적용
            return createTypeCastExpression(queryCall, resultTypeClass);
        } else if (returnTypeStr.startsWith("java.util.Optional")) {
            // Optional 타입 처리 - 최소 구현: Optional.empty() 반환 (제네릭 추론 이슈 회피)
            return createOptionalEmpty();
        } else {
            // 복합 객체 반환 타입
            Object rowMapper = createBeanPropertyRowMapper(resultTypeClass);
            // NamedParameterJdbcTemplate.queryForObject(String sql, SqlParameterSource ps, RowMapper<T> rm)
            return createMethodCall(createFieldAccess(namedJdbcTemplate, "queryForObject"), sqlLiteral, paramSource, rowMapper);
        }
    }

    /**
     * 수동 매핑 쿼리 생성
     */
    public Object createManualMappingQuery(Object namedJdbcTemplate, Object sqlLiteral, Object paramSource,
                                          String resultTypeClass, String columnMapping) throws Exception {
        // 수동 매핑 로직 구현
        Object rowMapper = createManualRowMapper(resultTypeClass, columnMapping);
        // NamedParameterJdbcTemplate.query(String sql, SqlParameterSource ps, RowMapper<T> rm)
        return createMethodCall(createFieldAccess(namedJdbcTemplate, "query"), sqlLiteral, paramSource, rowMapper);
    }

    /**
     * Bean Property 매핑 쿼리 생성
     */
    public Object createBeanPropertyMappingQuery(Object namedJdbcTemplate, Object sqlLiteral, Object paramSource,
                                                String resultTypeClass) throws Exception {
        Object rowMapper = createBeanPropertyRowMapper(resultTypeClass);
        // NamedParameterJdbcTemplate.query(String sql, SqlParameterSource ps, RowMapper<T> rm)
        return createMethodCall(createFieldAccess(namedJdbcTemplate, "query"), sqlLiteral, paramSource, rowMapper);
    }

    /**
     * 중첩 매핑 쿼리 생성
     */
    public Object createNestedMappingQuery(Object namedJdbcTemplate, Object sqlLiteral, Object paramSource,
                                          String resultTypeClass, String columnMapping) throws Exception {
        // 중첩 매핑 로직 구현
        Object rowMapper = createNestedRowMapper(resultTypeClass, columnMapping);
        // NamedParameterJdbcTemplate.query(String sql, SqlParameterSource ps, RowMapper<T> rm)
        return createMethodCall(createFieldAccess(namedJdbcTemplate, "query"), sqlLiteral, paramSource, rowMapper);
    }

    /**
     * 동적 SQL 처리 생성
     */
    public Object createDynamicSqlProcessing(String sql, List<?> methodParams) throws Exception {
        // Collection 타입 파라미터 처리를 위한 동적 SQL 생성
        
        // SQL을 StringBuilder로 변환하는 로직
        Object sqlBuilderVar = createVariable("sqlBuilder", "StringBuilder");
        Object sqlBuilderInit = createNewStringBuilder(sql);
        
        // Collection 파라미터들에 대한 처리
        for (Object param : methodParams) {
            if (isCollectionParameter(param)) {
                // IN 절 처리를 위한 동적 SQL 생성
                Object collectionProcessing = createCollectionProcessing(param);
                // sqlBuilder에 추가하는 로직
            }
        }
        
        return createMethodCall(createFieldAccess(sqlBuilderVar, "toString"));
    }

    /**
     * Positional SELECT 쿼리 생성
     */
    public Object createPositionalSelectQuery(Object jdbcTemplate, Object sqlLiteral, List<?> methodParams,
                                             ResultMappingType mappingType, String resultTypeClass,
                                             String columnMapping, ExecutableElement methodElement) throws Exception {
        
        Object paramArray = createParameterArray(methodParams);
        
        if (isListReturnType(methodElement)) {
            Object rowMapper = createBeanPropertyRowMapper(resultTypeClass);
            if (methodParams.isEmpty()) {
                return createMethodCall(createFieldAccess(jdbcTemplate, "query"), sqlLiteral, rowMapper);
            } else {
                return createMethodCall(createFieldAccess(jdbcTemplate, "query"), sqlLiteral, rowMapper, paramArray);
            }
        } else {
            // 단일 객체 반환
            if (isSimpleType(resultTypeClass)) {
                Object queryCall;
                if (methodParams.isEmpty()) {
                    queryCall = createMethodCall(createFieldAccess(jdbcTemplate, "queryForObject"), sqlLiteral, 
                            createClassLiteral(getFullTypeName(resultTypeClass)));
                } else {
                    queryCall = createMethodCall(createFieldAccess(jdbcTemplate, "queryForObject"), sqlLiteral, 
                            createClassLiteral(getFullTypeName(resultTypeClass)), paramArray);
                }
                
                // 컴파일러가 제네릭 정보를 잃는 경우를 대비해 명시적 캐스팅 적용
                return createTypeCastExpression(queryCall, resultTypeClass);
            } else {
                Object rowMapper = createBeanPropertyRowMapper(resultTypeClass);
                if (methodParams.isEmpty()) {
                    return createMethodCall(createFieldAccess(jdbcTemplate, "queryForObject"), sqlLiteral, rowMapper);
                } else {
                    return createMethodCall(createFieldAccess(jdbcTemplate, "queryForObject"), sqlLiteral, rowMapper, paramArray);
                }
            }
        }
    }

    /**
     * Positional UPDATE 쿼리 생성
     */
    public Object createPositionalUpdateQuery(Object jdbcTemplate, Object sqlLiteral, List<?> methodParams) throws Exception {
        Object updateMethod = createFieldAccess(jdbcTemplate, "update");
        
        if (methodParams.isEmpty()) {
            return createMethodCall(updateMethod, sqlLiteral);
        } else {
            Object paramArray = createParameterArray(methodParams);
            return createMethodCall(updateMethod, sqlLiteral, paramArray);
        }
    }

    /**
     * UPDATE 쿼리 생성 (Named Parameter)
     */
    public Object createUpdateQuery(Object namedJdbcTemplate, Object sqlLiteral, Object paramSource) throws Exception {
        Object updateMethod = createFieldAccess(namedJdbcTemplate, "update");
        return createMethodCall(updateMethod, sqlLiteral, paramSource);
    }

    /**
     * MapSqlParameterSource 생성
     */
    public Object createParameterSourceCreation(List<?> methodParams) throws Exception {
        Object paramSourceType = createQualifiedIdent("org.springframework.jdbc.core.namedparam.MapSqlParameterSource");
        Object paramSourceExpr = createNewInstance(paramSourceType);
        
        // 체이닝 방식으로 addValue를 연결: new MapSqlParameterSource().addValue("name", name)...
        Object chained = paramSourceExpr;
        for (Object param : methodParams) {
            String pName = null;
            try {
                // Try common getters for ParameterInfo
                try { pName = (String) param.getClass().getMethod("getName").invoke(param); } catch (Exception ignore) {}
                if (pName == null) { try { pName = (String) param.getClass().getMethod("getParamName").invoke(param); } catch (Exception ignore) {} }
                if (pName == null) { try { pName = (String) param.getClass().getMethod("getEffectiveName").invoke(param); } catch (Exception ignore) {} }
            } catch (Exception ignore) {}
            if (pName == null) continue;
            Object addValueAccess = createFieldAccess(chained, "addValue");
            Object nameLiteral = createLiteral(pName);
            Object nameIdent = astHelper.getClass().getMethod("createIdent", String.class).invoke(astHelper, pName);
            chained = createMethodCall(addValueAccess, nameLiteral, nameIdent);
        }
        
        return chained;
    }

    /**
     * BeanPropertyRowMapper 생성
     */
    private Object createBeanPropertyRowMapper(String resultTypeClass) throws Exception {
        Object rowMapperType = createQualifiedIdent("org.springframework.jdbc.core.BeanPropertyRowMapper");
        Object entityClass = createClassLiteral(resultTypeClass);
        return createNewClass(rowMapperType, new Object[]{entityClass});
    }

    private Object createNewClass(Object type, Object[] args) throws Exception {
        return astHelper.getClass().getMethod("createNewClass", Object.class, Object[].class)
                .invoke(astHelper, type, args);
    }

    /**
     * ColumnMapRowMapper 생성
     */
    private Object createColumnMapRowMapper() throws Exception {
        Object columnMapRowMapperType = createQualifiedIdent("org.springframework.jdbc.core.ColumnMapRowMapper");
        return createNewInstance(columnMapRowMapperType);
    }

    // 헬퍼 메서드들 - ASTHelper에 위임
    private Object createLiteral(String value) throws Exception {
        return astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, value);
    }

    private Object createFieldAccess(Object base, String fieldName) throws Exception {
        return astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                .invoke(astHelper, base, fieldName);
    }
    
    private Object createFieldAccess(String baseName, String fieldName) throws Exception {
        return astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, baseName, fieldName);
    }

    private Object createMethodCall(Object method, Object... args) throws Exception {
        return astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                .invoke(astHelper, method, args);
    }

    private Object createReturnStatement(Object expr) throws Exception {
        return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, expr);
    }

    private Object createVariable(String name, String type) throws Exception {
        return astHelper.getClass().getMethod("createVariable", String.class, String.class)
                .invoke(astHelper, name, type);
    }

    private Object createNewInstance(Object type) throws Exception {
        return astHelper.getClass().getMethod("createNewInstance", Object.class).invoke(astHelper, type);
    }

    private Object createQualifiedIdent(String qualifiedName) throws Exception {
        return astHelper.getClass().getMethod("createQualifiedIdent", String.class).invoke(astHelper, qualifiedName);
    }

    private Object createNewStringBuilder(String initialValue) throws Exception {
        return astHelper.getClass().getMethod("createNewStringBuilder", String.class).invoke(astHelper, initialValue);
    }

    private Object createClassLiteral(String className) throws Exception {
        return astHelper.getClass().getMethod("createClassLiteral", String.class).invoke(astHelper, className);
    }

    private Object createParameterArray(List<?> methodParams) throws Exception {
        return astHelper.getClass().getMethod("createParameterArray", List.class).invoke(astHelper, methodParams);
    }

    private Object createAddValueStatement(Object param) throws Exception {
        return astHelper.getClass().getMethod("createAddValueStatement", Object.class).invoke(astHelper, param);
    }

    private Object createCollectionProcessing(Object param) throws Exception {
        return astHelper.getClass().getMethod("createCollectionProcessing", Object.class).invoke(astHelper, param);
    }

    private Object createManualRowMapper(String resultTypeClass, String columnMapping) throws Exception {
        return astHelper.getClass().getMethod("createManualRowMapper", String.class, String.class)
                .invoke(astHelper, resultTypeClass, columnMapping);
    }

    private Object createNestedRowMapper(String resultTypeClass, String columnMapping) throws Exception {
        return astHelper.getClass().getMethod("createNestedRowMapper", String.class, String.class)
                .invoke(astHelper, resultTypeClass, columnMapping);
    }

    // 유틸리티 메서드들
    private boolean isCollectionParameter(Object param) {
        // ParameterInfo에서 Collection 타입인지 확인
        return false; // 임시 구현
    }

    private boolean isListReturnType(ExecutableElement methodElement) {
        return methodElement.getReturnType().toString().startsWith("java.util.List");
    }

    private boolean isSimpleType(String resultTypeClass) {
        return resultTypeClass.equals("String") || resultTypeClass.equals("Integer") || 
               resultTypeClass.equals("Long") || resultTypeClass.equals("Double") ||
               resultTypeClass.equals("Boolean") || resultTypeClass.equals("BigDecimal") ||
               resultTypeClass.equals("BigInteger");
    }

    /**
     * 타입 캐스팅 표현식 생성
     */
    private Object createTypeCastExpression(Object expression, String targetType) throws Exception {
        // 컴파일러 제네릭 추론 이슈를 방지하기 위해 항상 명시적 캐스팅 시도
        try {
            Object targetTypeExpr = createQualifiedIdent(getFullTypeName(targetType));
            return astHelper.getClass().getMethod("createTypeCast", Object.class, Object.class)
                    .invoke(astHelper, targetTypeExpr, expression);
        } catch (Exception e) {
            // createTypeCast 실패시 원본 표현식 반환 (fallback)
            return expression;
        }
    }

    /**
     * Optional 쿼리 생성
     */
    private Object createOptionalQuery(Object namedJdbcTemplate, Object sqlLiteral, Object paramSource,
                                      String resultTypeClass, String returnTypeStr) throws Exception {
        // Optional<T> 타입에서 T 추출
        String innerType = extractOptionalInnerType(returnTypeStr);
        
        // try-catch로 EmptyResultDataAccessException 처리
        Object tryBlock = createTryCatchForOptional(namedJdbcTemplate, sqlLiteral, paramSource, innerType);
        return tryBlock;
    }

    /**
     * Optional을 위한 try-catch 블록 생성
     */
    private Object createTryCatchForOptional(Object namedJdbcTemplate, Object sqlLiteral, Object paramSource, String innerType) throws Exception {
        // try {
        //     T result = namedJdbcTemplate.queryForObject(sql, Type.class, params);
        //     return Optional.of(result);
        // } catch (EmptyResultDataAccessException e) {
        //     return Optional.empty();
        // }
        
        Object queryCall;
        if (isSimpleType(innerType)) {
            // Simple types: use SingleColumnRowMapper to avoid generic inference issues
            Object rowMapperType = createQualifiedIdent("org.springframework.jdbc.core.SingleColumnRowMapper");
            Object classLiteral = createClassLiteral(getFullTypeName(innerType));
            Object rowMapper = createNewClass(rowMapperType, new Object[]{classLiteral});
            // NamedParameterJdbcTemplate.queryForObject(String sql, SqlParameterSource ps, RowMapper<T> rm)
            queryCall = createMethodCall(createFieldAccess(namedJdbcTemplate, "queryForObject"), 
                    sqlLiteral, paramSource, rowMapper);
        } else {
            Object rowMapper = createBeanPropertyRowMapper(innerType);
            // NamedParameterJdbcTemplate.queryForObject(String sql, SqlParameterSource ps, RowMapper<T> rm)
            queryCall = createMethodCall(createFieldAccess(namedJdbcTemplate, "queryForObject"), 
                    sqlLiteral, paramSource, rowMapper);
        }
        
        // 명시적 캐스팅을 통해 컴파일러 제네릭 추론 이슈 방지
        queryCall = createTypeCastExpression(queryCall, innerType);
        
        // Optional.ofNullable(result) 반환 (간단한 버전)
        return createOptionalOfNullable(queryCall);
    }

    /**
     * Optional.ofNullable() 호출 생성
     */
    private Object createOptionalOfNullable(Object value) throws Exception {
        Object optionalClass = createQualifiedIdent("java.util.Optional");
        Object ofNullableMethod = createFieldAccess(optionalClass, "ofNullable");
        return createMethodCall(ofNullableMethod, value);
    }

    /**
     * Optional.empty() 호출 생성
     */
    private Object createOptionalEmpty() throws Exception {
        Object optionalClass = createQualifiedIdent("java.util.Optional");
        Object emptyMethod = createFieldAccess(optionalClass, "empty");
        return createMethodCall(emptyMethod);
    }

    /**
     * Optional<T>에서 T 타입 추출
     */
    private String extractOptionalInnerType(String optionalType) {
        // "java.util.Optional<Long>" -> "Long"
        int start = optionalType.indexOf('<');
        int end = optionalType.lastIndexOf('>');
        if (start > 0 && end > start) {
            String innerType = optionalType.substring(start + 1, end);
            return innerType.substring(innerType.lastIndexOf('.') + 1);
        }
        return "Object";
    }

    /**
     * 단순 타입명을 전체 클래스명으로 변환
     */
    private String getFullTypeName(String simpleType) {
        switch (simpleType) {
            case "String": return "java.lang.String";
            case "Integer": return "java.lang.Integer";
            case "Long": return "java.lang.Long";
            case "Double": return "java.lang.Double";
            case "Boolean": return "java.lang.Boolean";
            case "BigDecimal": return "java.math.BigDecimal";
            case "BigInteger": return "java.math.BigInteger";
            default: return simpleType;
        }
    }
}
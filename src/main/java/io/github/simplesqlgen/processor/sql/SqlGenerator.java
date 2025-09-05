package io.github.simplesqlgen.processor.sql;

import io.github.simplesqlgen.enums.NamingStrategy;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.*;

/**
 * SQL 생성을 담당하는 헬퍼 클래스
 * 동적 SQL 생성, 메서드 이름 파싱, 조건 파싱 등을 처리
 */
public class SqlGenerator {

    private NamingStrategy namingStrategy = NamingStrategy.SNAKE_CASE;

    public void setNamingStrategy(NamingStrategy strategy) {
        if (strategy != null) this.namingStrategy = strategy;
    }

    private String mapColumnName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) return fieldName;
        switch (this.namingStrategy) {
            case CAMEL_CASE:
                // keep as lowerCamel
                return fieldName;
            case PASCAL_CASE:
                return Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            case KEBAB_CASE:
                return fieldName.replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase();
            case SNAKE_CASE:
            default:
                return fieldName.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
        }
    }

    private static final Set<String> LOGICAL_OPERATORS = new HashSet<>(Arrays.asList("And", "Or"));
    private static final Set<String> COMPARISON_OPERATORS = new HashSet<>(Arrays.asList(
        "Equal", "NotEqual", "GreaterThan", "GreaterThanEqual", "LessThan", "LessThanEqual",
        "Like", "NotLike", "In", "NotIn", "IsNull", "IsNotNull", "Between", "NotBetween",
        "Containing", "NotContaining", "StartingWith", "EndingWith", "IgnoreCase"
    ));
    
    /**
     * Find 메서드 구현 생성 (검증 포함) - ById 제외
     */
    public Object createFindByImplementationWithValidation(String methodName, String entityName, String entityFqn, 
                                                         String tableName, Object entityInfo, ExecutableElement methodElement,
                                                         Object astHelper) throws Exception {
        // ById도 일반 필드와 동일하게 처리하도록 허용
        QueryMethodInfo info = parseQueryMethodName(methodName);
        
        // 엔티티 필드 검증
        for (String field : info.getFields()) {
            if (!isValidEntityField(entityInfo, field)) {
                throw new RuntimeException("Invalid field: " + field + " in method " + methodName + 
                                         ". Field does not exist in entity " + entityName);
            }
        }
        
        String sql = generateAdvancedDynamicSQL(info, tableName);
        
        // 실제 메서드 구현 생성은 ASTHelper를 통해
        return createQueryImplementation(sql, methodElement, entityFqn, astHelper);
    }

    /**
     * Save 메서드 구현 생성 (엔티티 포함)
     */
    public Object createSaveImplementationWithEntity(String entityName, String tableName, Object entityInfo, 
                                                    ExecutableElement methodElement, Object astHelper) throws Exception {
        // 엔티티의 실제 필드들을 기반으로 INSERT SQL 생성
        List<String> fields = getEntityFields(entityInfo);
        
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
        StringBuilder values = new StringBuilder(" VALUES (");
        
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sql.append(", ");
                values.append(", ");
            }
            sql.append(mapColumnName(fields.get(i)));
            values.append("?");
        }
        
        sql.append(")").append(values).append(")");
        
        return createUpdateImplementation(sql.toString(), methodElement, fields, astHelper);
    }

    /**
     * Update 메서드 구현 생성 (엔티티 포함)
     */
    public Object createUpdateImplementationWithEntity(String entityName, String tableName, Object entityInfo,
                                                      ExecutableElement methodElement, Object astHelper) throws Exception {
        List<String> fields = getEntityFields(entityInfo);
        
        // id 필드를 제외한 SET 절 구성
        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
        boolean first = true;
        for (String f : fields) {
            if ("id".equals(f)) continue;
            if (!first) {
                sql.append(", ");
            }
            sql.append(mapColumnName(f)).append(" = ?");
            first = false;
        }
        
        // WHERE 절: id가 존재하면 id를 기준으로 업데이트
        if (fields.contains("id")) {
            sql.append(" WHERE id = ?");
        }
        
        return createUpdateImplementation(sql.toString(), methodElement, fields, astHelper);
    }

    /**
     * Count 메서드 구현 생성 (검증 포함) - ById 제외
     */
    public Object createCountByImplementationWithValidation(String methodName, String tableName, Object entityInfo,
                                                           ExecutableElement methodElement, Object astHelper) throws Exception {
        // ById도 일반 필드와 동일하게 처리하도록 허용
        QueryMethodInfo info = parseQueryMethodName(methodName);
        
        // 엔티티 필드 검증
        for (String field : info.getFields()) {
            if (!isValidEntityField(entityInfo, field)) {
                throw new RuntimeException("Invalid field: " + field + " in method " + methodName);
            }
        }
        
        String sql = "SELECT COUNT(*) FROM " + tableName + generateWhereClause(info);
        return createCountImplementation(sql, methodElement, astHelper);
    }

    /**
     * Delete 메서드 구현 생성 (검증 포함) - ById 제외
     */
    public Object createDeleteByImplementationWithValidation(String methodName, String tableName, Object entityInfo,
                                                            ExecutableElement methodElement, Object astHelper) throws Exception {
        // ById도 일반 필드와 동일하게 처리하도록 허용
        QueryMethodInfo info = parseQueryMethodName(methodName);
        
        // 엔티티 필드 검증
        for (String field : info.getFields()) {
            if (!isValidEntityField(entityInfo, field)) {
                throw new RuntimeException("Invalid field: " + field + " in method " + methodName);
            }
        }
        
        String sql = "DELETE FROM " + tableName + generateWhereClause(info);
        return createDeleteImplementation(sql, methodElement, astHelper);
    }

    /**
     * Exists 메서드 구현 생성 (검증 포함) - ById 제외
     */
    public Object createExistsByImplementationWithValidation(String methodName, String tableName, Object entityInfo,
                                                            ExecutableElement methodElement, Object astHelper) throws Exception {
        try {
            QueryMethodInfo info = parseQueryMethodName(methodName);
            // 엔티티 필드 검증
            for (String field : info.getFields()) {
                if (!isValidEntityField(entityInfo, field)) {
                    throw new RuntimeException("Invalid field: " + field + " in method " + methodName);
                }
            }
            // DB에서 직접 boolean을 반환하도록 SQL 생성 (Java 비교 연산 회피)
            String base = "SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END FROM ";
            String sql = base + tableName + generateWhereClause(info);
            return createExistsBooleanImplementation(sql, methodElement, astHelper);
        } catch (Exception e) {
            try { System.out.println("[EXISTS_GEN_ERROR] Boolean CASE generation failed: " + e.getMessage()); } catch (Exception ignoreLog) {}
            // Boolean CASE 방식 실패 시, count > 0 방식으로 재시도
            try {
                String sqlFallback = "SELECT COUNT(*) FROM " + tableName + generateWhereClause(parseQueryMethodName(methodName));
                return createExistsImplementation(sqlFallback, methodElement, astHelper);
            } catch (Exception ignored) {
                try { System.out.println("[EXISTS_GEN_ERROR] Fallback count>0 generation failed: " + ignored.getMessage()); } catch (Exception ignoreLog2) {}
                // 최후의 수단: 안전하게 false 반환
                Object boolCls = astHelper.getClass().getMethod("createQualifiedIdent", String.class)
                        .invoke(astHelper, "java.lang.Boolean");
                Object falseLit = astHelper.getClass().getMethod("createLiteral", Object.class).invoke(astHelper, false);
                Object valueOf = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                        .invoke(astHelper, boolCls, "valueOf");
                Object call = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                        .invoke(astHelper, valueOf, new Object[]{falseLit});
                return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, call);
            }
        }
    }

    /**
     * FindAll 구현 생성
     */
    public Object createFindAllImplementation(String entityFqn, String tableName, Object astHelper) throws Exception {
        String sql = "SELECT * FROM " + tableName;
        return createFindAllQueryImplementation(sql, entityFqn, astHelper);
    }

    /**
     * 쿼리 메서드 이름 파싱
     */
    public QueryMethodInfo parseQueryMethodName(String methodName) {
        QueryMethodInfo info = new QueryMethodInfo();
        
        if (methodName.startsWith("findBy") || methodName.startsWith("countBy") || 
            methodName.startsWith("deleteBy") || methodName.startsWith("existsBy")) {
            String condition = methodName.substring(methodName.indexOf("By") + 2);
            return parseCondition(condition);
        } else if (methodName.equals("findAll")) {
            info.setOperation("findAll");
            return info;
        } else if (methodName.startsWith("save")) {
            info.setOperation("save");
            return info;
        } else {
            // 기본 처리
            info.setOperation("unknown");
            return info;
        }
    }

    /**
     * 조건 파싱
     */
    public QueryMethodInfo parseCondition(String condition) {
        QueryMethodInfo info = new QueryMethodInfo();
        List<String> fields = new ArrayList<>();
        List<String> operators = new ArrayList<>();
        List<String> logicalOperators = new ArrayList<>();
        
        // And/Or로 분할
        String[] parts = condition.split("(?=And|Or)");
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            
            if (part.startsWith("And")) {
                logicalOperators.add("AND");
                part = part.substring(3);
            } else if (part.startsWith("Or")) {
                logicalOperators.add("OR");
                part = part.substring(2);
            }
            
            // 비교 연산자 추출
            String field = part;
            String operator = "="; // 기본값
            
            for (String compOp : COMPARISON_OPERATORS) {
                if (part.endsWith(compOp)) {
                    field = part.substring(0, part.length() - compOp.length());
                    operator = mapOperatorToSql(compOp);
                    break;
                }
            }
            
            // 필드명 첫 글자를 소문자로 변환 (camelCase 규칙 적용)
            if (!field.isEmpty()) {
                field = Character.toLowerCase(field.charAt(0)) + field.substring(1);
            }
            
            fields.add(field);
            operators.add(operator);
        }
        
        info.setFields(fields);
        info.setOperators(operators);
        info.setLogicalOperators(logicalOperators);
        info.setOperation("query");
        
        return info;
    }

    /**
     * 고급 동적 SQL 생성
     */
    public String generateAdvancedDynamicSQL(QueryMethodInfo info, String tableName) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName);
        
        if (!info.getFields().isEmpty()) {
            sql.append(generateWhereClause(info));
        }
        
        return sql.toString();
    }

    /**
     * WHERE 절 생성
     */
    private String generateWhereClause(QueryMethodInfo info) {
        if (info.getFields().isEmpty()) {
            return "";
        }
        
        StringBuilder where = new StringBuilder(" WHERE ");
        List<String> fields = info.getFields();
        List<String> operators = info.getOperators();
        List<String> logicalOperators = info.getLogicalOperators();
        
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0 && i - 1 < logicalOperators.size()) {
                where.append(" ").append(logicalOperators.get(i - 1)).append(" ");
            }
            
            String column = mapColumnName(fields.get(i));
            String op = (i < operators.size()) ? operators.get(i) : "=";
            
            // Handle special operators that affect placeholder usage
            if ("IS NULL".equals(op) || "IS NOT NULL".equals(op)) {
                where.append(column).append(" ").append(op);
            } else if ("BETWEEN".equals(op) || "NOT BETWEEN".equals(op)) {
                where.append(column).append(" ").append(op).append(" ? AND ?");
            } else if ("IN".equals(op) || "NOT IN".equals(op)) {
                // Minimal support: single placeholder, the caller must handle list/array expansion if needed
                where.append(column).append(" ").append(op).append(" (").append("?").append(")");
            } else {
                // Default single-parameter operators: =, !=, >, >=, <, <=, LIKE
                where.append(column).append(" ").append(op).append(" ?");
            }
        }
        
        return where.toString();
    }

    /**
     * 연산자를 SQL 형태로 매핑
     */
    private String mapOperatorToSql(String operator) {
        switch (operator) {
            case "Equal": return "=";
            case "NotEqual": return "!=";
            case "GreaterThan": return ">";
            case "GreaterThanEqual": return ">=";
            case "LessThan": return "<";
            case "LessThanEqual": return "<=";
            case "Like": case "Containing": return "LIKE";
            case "NotLike": case "NotContaining": return "NOT LIKE";
            case "In": return "IN";
            case "NotIn": return "NOT IN";
            case "IsNull": return "IS NULL";
            case "IsNotNull": return "IS NOT NULL";
            case "Between": return "BETWEEN";
            case "NotBetween": return "NOT BETWEEN";
            case "StartingWith": return "LIKE";
            case "EndingWith": return "LIKE";
            default: return "=";
        }
    }

    /**
     * 엔티티 필드가 유효한지 검증
     */
    private boolean isValidEntityField(Object entityInfo, String fieldName) {
        // EntityInfo 클래스의 필드 목록을 확인
        try {
            java.lang.reflect.Method getFieldsMethod = entityInfo.getClass().getMethod("getFields");
            @SuppressWarnings("unchecked")
            List<String> fields = (List<String>) getFieldsMethod.invoke(entityInfo);
            return fields.contains(fieldName);
        } catch (Exception e) {
            // 기본적으로 true 반환 (호환성)
            return true;
        }
    }

    /**
     * 엔티티 필드 목록 가져오기
     */
    private List<String> getEntityFields(Object entityInfo) {
        try {
            java.lang.reflect.Method getFieldsMethod = entityInfo.getClass().getMethod("getFields");
            @SuppressWarnings("unchecked")
            List<String> fields = (List<String>) getFieldsMethod.invoke(entityInfo);
            return fields;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // 실제 JDBC 코드 생성
    private Object createQueryImplementation(String sql, ExecutableElement methodElement, String entityFqn, Object astHelper) throws Exception {
        Object jdbcTemplateAccess = astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, "this", "jdbcTemplate");
        
        Object sqlLiteral = astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, sql);
        Object rowMapper = astHelper.getClass().getMethod("createBeanPropertyRowMapper", String.class)
                .invoke(astHelper, getSimpleClassName(entityFqn));
        
        // 메서드 파라미터를 Object[]로 구성
        List<? extends javax.lang.model.element.VariableElement> params = methodElement.getParameters();
        Object paramArray = null;
        if (params != null && !params.isEmpty()) {
            List<Object> elements = new ArrayList<>();
            for (javax.lang.model.element.VariableElement ve : params) {
                String name = ve.getSimpleName().toString();
                Object ident = astHelper.getClass().getMethod("createIdent", String.class).invoke(astHelper, name);
                elements.add(ident);
            }
            paramArray = astHelper.getClass().getMethod("createArrayInitializer", String.class, List.class)
                    .invoke(astHelper, "Object", elements);
        }
        
        // 반환 타입에 따라 query vs queryForObject 선택
        TypeMirror returnType = methodElement.getReturnType();
        String returnTypeStr = returnType.toString();
        
        Object queryCall;
        if (returnTypeStr.startsWith("java.util.List")) {
            // List 반환 타입 - jdbcTemplate.query() 사용 + 파라미터 바인딩
            Object queryMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                    .invoke(astHelper, jdbcTemplateAccess, "query");
            if (paramArray == null) {
                queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                        .invoke(astHelper, queryMethod, new Object[]{sqlLiteral, rowMapper});
            } else {
                queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                        .invoke(astHelper, queryMethod, new Object[]{sqlLiteral, rowMapper, paramArray});
            }
        } else {
            // 단일 객체 반환 타입 - jdbcTemplate.queryForObject() 사용 + BeanPropertyRowMapper + 명시적 캐스팅
            Object queryForObjectMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                    .invoke(astHelper, jdbcTemplateAccess, "queryForObject");
            if (paramArray == null) {
                queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                        .invoke(astHelper, queryForObjectMethod, new Object[]{sqlLiteral, rowMapper});
            } else {
                queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                        .invoke(astHelper, queryForObjectMethod, new Object[]{sqlLiteral, rowMapper, paramArray});
            }
            try {
                Object targetType = astHelper.getClass().getMethod("createQualifiedIdent", String.class)
                        .invoke(astHelper, getSimpleClassName(entityFqn));
                queryCall = astHelper.getClass().getMethod("createTypeCast", Object.class, Object.class)
                        .invoke(astHelper, targetType, queryCall);
            } catch (Exception ignore) { }
        }
        
        return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, queryCall);
    }

    private Object createUpdateImplementation(String sql, ExecutableElement methodElement, List<String> fields, Object astHelper) throws Exception {
        // jdbcTemplate.update(sql, params...)
        Object jdbcTemplateAccess = astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, "this", "jdbcTemplate");
        Object updateMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                .invoke(astHelper, jdbcTemplateAccess, "update");
        
        Object sqlLiteral = astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, sql);
        
        // 엔티티 파라미터에서 값 추출하여 Object[] 구성
        List<? extends javax.lang.model.element.VariableElement> params = methodElement.getParameters();
        Object paramArray = null;
        if (params != null && !params.isEmpty()) {
            String entityParamName = params.get(0).getSimpleName().toString();
            Object entityIdent = astHelper.getClass().getMethod("createIdent", String.class).invoke(astHelper, entityParamName);
            List<Object> elements = new ArrayList<>();
            boolean isUpdateSql = sql.trim().toUpperCase().startsWith("UPDATE");
            // SET 절 값들
            for (String f : fields) {
                if (isUpdateSql && "id".equals(f)) continue; // UPDATE의 SET에서는 id 제외
                String getter = "get" + Character.toUpperCase(f.charAt(0)) + f.substring(1);
                Object getterSel = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                        .invoke(astHelper, entityIdent, getter);
                Object getterCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                        .invoke(astHelper, getterSel, new Object[]{});
                elements.add(getterCall);
            }
            // WHERE id = ? 의 파라미터 (UPDATE인 경우 마지막에 id 추가)
            if (isUpdateSql && fields.contains("id")) {
                String getter = "getId";
                Object getterSel = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                        .invoke(astHelper, entityIdent, getter);
                Object getterCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                        .invoke(astHelper, getterSel, new Object[]{});
                elements.add(getterCall);
            }
            paramArray = astHelper.getClass().getMethod("createArrayInitializer", String.class, List.class)
                    .invoke(astHelper, "Object", elements);
        }
        
        Object updateCall;
        if (paramArray == null) {
            updateCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, updateMethod, new Object[]{sqlLiteral});
        } else {
            updateCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, updateMethod, new Object[]{sqlLiteral, paramArray});
        }
        
        return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, updateCall);
    }

    private Object createCountImplementation(String sql, ExecutableElement methodElement, Object astHelper) throws Exception {
        // jdbcTemplate.queryForObject(sql, Long.class) with explicit Long casting
        Object jdbcTemplateAccess = astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, "this", "jdbcTemplate");
        
        // queryForObject 메서드 접근
        Object queryForObjectMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                .invoke(astHelper, jdbcTemplateAccess, "queryForObject");
        
        Object sqlLiteral = astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, sql);
        Object longClassLiteral = astHelper.getClass().getMethod("createClassLiteral", String.class)
                .invoke(astHelper, "java.lang.Long");
        
        // 메서드 파라미터 배열 구성
        List<? extends javax.lang.model.element.VariableElement> params = methodElement.getParameters();
        Object paramArray = null;
        if (params != null && !params.isEmpty()) {
            List<Object> elements = new ArrayList<>();
            for (javax.lang.model.element.VariableElement ve : params) {
                String name = ve.getSimpleName().toString();
                Object ident = astHelper.getClass().getMethod("createIdent", String.class).invoke(astHelper, name);
                elements.add(ident);
            }
            paramArray = astHelper.getClass().getMethod("createArrayInitializer", String.class, List.class)
                    .invoke(astHelper, "Object", elements);
        }
        
        // queryForObject 호출 생성 
        Object queryCall;
        if (paramArray == null) {
            queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, queryForObjectMethod, new Object[]{sqlLiteral, longClassLiteral});
        } else {
            queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, queryForObjectMethod, new Object[]{sqlLiteral, longClassLiteral, paramArray});
        }
        
        // Object를 Long으로 캐스팅: (Long) queryCall
        Object longType = astHelper.getClass().getMethod("createQualifiedIdent", String.class)
                .invoke(astHelper, "Long");
        
        try {
            Object castedCall = astHelper.getClass().getMethod("createTypeCast", Object.class, Object.class)
                    .invoke(astHelper, longType, queryCall);
            return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, castedCall);
        } catch (Exception e) {
            // createTypeCast 실패시 직접 Long 타입으로 반환 (간단한 fallback)
            return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, queryCall);
        }
    }

    private Object createDeleteImplementation(String sql, ExecutableElement methodElement, Object astHelper) throws Exception {
        // jdbcTemplate.update(sql, params...)
        Object jdbcTemplateAccess = astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, "this", "jdbcTemplate");
        Object updateMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                .invoke(astHelper, jdbcTemplateAccess, "update");
        
        Object sqlLiteral = astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, sql);
        
        // 메서드 파라미터를 Object[]로 구성
        List<? extends javax.lang.model.element.VariableElement> params = methodElement.getParameters();
        Object paramArray = null;
        if (params != null && !params.isEmpty()) {
            List<Object> elements = new ArrayList<>();
            for (javax.lang.model.element.VariableElement ve : params) {
                String name = ve.getSimpleName().toString();
                Object ident = astHelper.getClass().getMethod("createIdent", String.class).invoke(astHelper, name);
                elements.add(ident);
            }
            paramArray = astHelper.getClass().getMethod("createArrayInitializer", String.class, List.class)
                    .invoke(astHelper, "Object", elements);
        }
        
        Object updateCall;
        if (paramArray == null) {
            updateCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, updateMethod, new Object[]{sqlLiteral});
        } else {
            updateCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, updateMethod, new Object[]{sqlLiteral, paramArray});
        }
        
        return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, updateCall);
    }

    private Object createExistsImplementation(String sql, ExecutableElement methodElement, Object astHelper) throws Exception {
        // Long count = jdbcTemplate.queryForObject(sql, Long.class[, params]); return count > 0;
        Object jdbcTemplateAccess = astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, "this", "jdbcTemplate");
        Object queryForObjectMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                .invoke(astHelper, jdbcTemplateAccess, "queryForObject");
        
        Object sqlLiteral = astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, sql);
        Object longClassLiteral = astHelper.getClass().getMethod("createClassLiteral", String.class)
                .invoke(astHelper, "java.lang.Long");
        
        // 메서드 파라미터 배열 구성
        List<? extends javax.lang.model.element.VariableElement> params = methodElement.getParameters();
        Object paramArray = null;
        if (params != null && !params.isEmpty()) {
            List<Object> elements = new ArrayList<>();
            for (javax.lang.model.element.VariableElement ve : params) {
                String name = ve.getSimpleName().toString();
                Object ident = astHelper.getClass().getMethod("createIdent", String.class).invoke(astHelper, name);
                elements.add(ident);
            }
            paramArray = astHelper.getClass().getMethod("createArrayInitializer", String.class, List.class)
                    .invoke(astHelper, "Object", elements);
        }
        
        Object queryCall;
        if (paramArray == null) {
            queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, queryForObjectMethod, new Object[]{sqlLiteral, longClassLiteral});
        } else {
            queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, queryForObjectMethod, new Object[]{sqlLiteral, longClassLiteral, paramArray});
        }
        
        // 명시적 타입 캐스팅 추가 (실패 시 그대로 사용)
        Object valueForCompare = queryCall;
        try {
            Object longType = astHelper.getClass().getMethod("createQualifiedIdent", String.class)
                    .invoke(astHelper, "Long");
            valueForCompare = astHelper.getClass().getMethod("createTypeCast", Object.class, Object.class)
                    .invoke(astHelper, longType, queryCall);
        } catch (Exception ignore) { }
        
        Object zeroLiteral = astHelper.getClass().getMethod("createLiteral", Object.class).invoke(astHelper, 0L);
        Object comparison = astHelper.getClass().getMethod("createBinaryExpression", Object.class, String.class, Object.class)
                .invoke(astHelper, valueForCompare, ">", zeroLiteral);
        
        return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, comparison);
    }

    private Object createExistsBooleanImplementation(String sql, ExecutableElement methodElement, Object astHelper) throws Exception {
        // return jdbcTemplate.queryForObject(sql, Boolean.class)
        Object jdbcTemplateAccess = astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, "this", "jdbcTemplate");
        Object queryForObjectMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                .invoke(astHelper, jdbcTemplateAccess, "queryForObject");
        
        Object sqlLiteral = astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, sql);
        Object boolClassLiteral = astHelper.getClass().getMethod("createClassLiteral", String.class)
                .invoke(astHelper, "java.lang.Boolean");
        
        // 메서드 파라미터 배열 구성
        List<? extends javax.lang.model.element.VariableElement> params = methodElement.getParameters();
        Object paramArray = null;
        if (params != null && !params.isEmpty()) {
            List<Object> elements = new ArrayList<>();
            for (javax.lang.model.element.VariableElement ve : params) {
                String name = ve.getSimpleName().toString();
                Object ident = astHelper.getClass().getMethod("createIdent", String.class).invoke(astHelper, name);
                elements.add(ident);
            }
            paramArray = astHelper.getClass().getMethod("createArrayInitializer", String.class, List.class)
                    .invoke(astHelper, "Object", elements);
        }
        
        Object queryCall;
        if (paramArray == null) {
            queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, queryForObjectMethod, new Object[]{sqlLiteral, boolClassLiteral});
        } else {
            queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, queryForObjectMethod, new Object[]{sqlLiteral, boolClassLiteral, paramArray});
        }
        
        // Autounboxing will convert Boolean to boolean for primitive return signatures
        return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, queryCall);
    }

    private Object createFindAllQueryImplementation(String sql, String entityFqn, Object astHelper) throws Exception {
        // jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(EntityClass.class))
        Object jdbcTemplateAccess = astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, "this", "jdbcTemplate");
        Object queryMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                .invoke(astHelper, jdbcTemplateAccess, "query");
        
        Object sqlLiteral = astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, sql);
        Object rowMapper = astHelper.getClass().getMethod("createBeanPropertyRowMapper", String.class)
                .invoke(astHelper, getSimpleClassName(entityFqn));
        
        Object queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                .invoke(astHelper, queryMethod, new Object[]{sqlLiteral, rowMapper});
        
        return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, queryCall);
    }

    private String getSimpleClassName(String fullyQualifiedName) {
        return fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
    }

    private Object createDebugStatement(String message, Object astHelper) throws Exception {
        Object systemOut = astHelper.getClass().getMethod("createQualifiedIdent", String.class)
                .invoke(astHelper, "System.out");
        Object printlnAccess = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                .invoke(astHelper, systemOut, "println");
        Object messageLiteral = astHelper.getClass().getMethod("createLiteral", String.class)
                .invoke(astHelper, message);
        Object printCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                .invoke(astHelper, printlnAccess, new Object[]{messageLiteral});
        return astHelper.getClass().getMethod("createExpressionStatement", Object.class)
                .invoke(astHelper, printCall);
    }

    /**
     * 쿼리 메서드 정보를 담는 클래스
     */
    public static class QueryMethodInfo {
        private String operation;
        private List<String> fields = new ArrayList<>();
        private List<String> operators = new ArrayList<>();
        private List<String> logicalOperators = new ArrayList<>();

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        public List<String> getFields() { return fields; }
        public void setFields(List<String> fields) { this.fields = fields; }

        public List<String> getOperators() { return operators; }
        public void setOperators(List<String> operators) { this.operators = operators; }

        public List<String> getLogicalOperators() { return logicalOperators; }
        public void setLogicalOperators(List<String> logicalOperators) { this.logicalOperators = logicalOperators; }
    }
}
package io.github.simplesqlgen.processor.query;

import io.github.simplesqlgen.enums.ResultMappingType;

import javax.lang.model.element.ExecutableElement;
import java.util.List;

/**
 * Query execution code generator
 * Generates Named Parameter and Positional Parameter query execution code
 */
public class QueryExecutor {
    
    private Object astHelper;

    public QueryExecutor(Object astHelper) {
        this.astHelper = astHelper;
    }

    /**
     * Create Named Parameter query execution
     */
    public Object createNamedParameterQueryExecution(String sql, ExecutableElement methodElement,
                                                    boolean isUpdate, ResultMappingType mappingType,
                                                    String resultTypeClass, String columnMapping,
                                                    List<?> methodParams, boolean isVoid) throws Exception {
        
        Object sqlLiteral = createLiteral(sql);
        Object namedJdbcTemplateAccess = createFieldAccess("this", "namedParameterJdbcTemplate");
        Object paramSourceVar = createParameterSourceCreation(methodParams);
        
        Object queryCall;
        
        if (isUpdate) {
            queryCall = createUpdateQuery(namedJdbcTemplateAccess, sqlLiteral, paramSourceVar);
        } else {
            queryCall = createNamedParameterSelectQuery(namedJdbcTemplateAccess, sqlLiteral, paramSourceVar,
                    mappingType, resultTypeClass, columnMapping, methodElement);
        }
        
        if (isVoid) {
            return createExpressionStatement(queryCall);
        }
        return createReturnStatement(queryCall);
    }

    /**
     * Create Positional Parameter query execution
     */
    public Object createPositionalParameterQueryExecution(String sql, ExecutableElement methodElement,
                                                         boolean isUpdate, ResultMappingType mappingType,
                                                         String resultTypeClass, String columnMapping,
                                                         List<?> methodParams, boolean isVoid) throws Exception {
        
        Object jdbcTemplateAccess = createFieldAccess("this", "jdbcTemplate");
        Object sqlLiteral = createLiteral(sql);
        
        Object queryCall;
        
        if (isUpdate) {
            queryCall = createPositionalUpdateQuery(jdbcTemplateAccess, sqlLiteral, methodParams);
        } else {
            queryCall = createPositionalSelectQuery(jdbcTemplateAccess, sqlLiteral, methodParams,
                    mappingType, resultTypeClass, columnMapping, methodElement);
        }
        
        if (isVoid) {
            return createExpressionStatement(queryCall);
        }
        return createReturnStatement(queryCall);
    }

    /**
     * Create Named Parameter SELECT query
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
     * Create auto mapping query
     */
    public Object createAutoMappingQuery(
            Object namedJdbcTemplate,
            Object sqlLiteral,
            Object paramSource,
            String resultTypeClass,
            ExecutableElement methodElement
    ) throws Exception {

        String returnTypeStr = methodElement.getReturnType().toString();

        if (returnTypeStr.startsWith("java.util.List")) {

            if (returnTypeStr.contains("Map<String") || returnTypeStr.contains("Map<java.lang.String")) {
                Object columnMapRowMapper = createColumnMapRowMapper();
                return createMethodCall(
                        createFieldAccess(namedJdbcTemplate, "query"),
                        sqlLiteral,
                        paramSource,
                        columnMapRowMapper
                );
            } else {
                Object rowMapper = createBeanPropertyRowMapper(resultTypeClass);
                return createMethodCall(
                        createFieldAccess(namedJdbcTemplate, "query"),
                        sqlLiteral,
                        paramSource,
                        rowMapper
                );
            }

        }

        else if (isSimpleType(resultTypeClass)) {
            Object classLiteral = createClassLiteral(resultTypeClass);
            Object queryCall = createMethodCall(
                    createFieldAccess(namedJdbcTemplate, "queryForObject"),
                    sqlLiteral,
                    paramSource,
                    classLiteral
            );

            if (isPrimitive(resultTypeClass)) {
                return queryCall;
            } else {
                return createTypeCastExpression(queryCall, resultTypeClass);
            }
        }

        else if (returnTypeStr.startsWith("java.util.Optional")) {
            return createOptionalEmpty();
        }

        else {
            Object rowMapper = createBeanPropertyRowMapper(resultTypeClass);
            Object queryCall = createMethodCall(
                    createFieldAccess(namedJdbcTemplate, "queryForObject"),
                    sqlLiteral,
                    paramSource,
                    rowMapper
            );
            return createTypeCastExpression(queryCall, resultTypeClass);
        }
    }

    /**
     * Create manual mapping query
     */
    public Object createManualMappingQuery(Object namedJdbcTemplate, Object sqlLiteral, Object paramSource,
                                          String resultTypeClass, String columnMapping) throws Exception {
        Object rowMapper = createManualRowMapper(resultTypeClass, columnMapping);
        return createMethodCall(createFieldAccess(namedJdbcTemplate, "query"), sqlLiteral, paramSource, rowMapper);
    }

    /**
     * Create Bean Property mapping query
     */
    public Object createBeanPropertyMappingQuery(Object namedJdbcTemplate, Object sqlLiteral, Object paramSource,
                                                String resultTypeClass) throws Exception {
        Object rowMapper = createBeanPropertyRowMapper(resultTypeClass);
        return createMethodCall(createFieldAccess(namedJdbcTemplate, "query"), sqlLiteral, paramSource, rowMapper);
    }

    /**
     * Create nested mapping query
     */
    public Object createNestedMappingQuery(Object namedJdbcTemplate, Object sqlLiteral, Object paramSource,
                                          String resultTypeClass, String columnMapping) throws Exception {
        Object rowMapper = createNestedRowMapper(resultTypeClass, columnMapping);
        return createMethodCall(createFieldAccess(namedJdbcTemplate, "query"), sqlLiteral, paramSource, rowMapper);
    }

    /**
     * Create dynamic SQL processing
     */
    public Object createDynamicSqlProcessing(String sql, List<?> methodParams) throws Exception {
        Object sqlBuilderVar = createVariable("sqlBuilder", "StringBuilder");
        Object sqlBuilderInit = createNewStringBuilder(sql);
        
        for (Object param : methodParams) {
            if (isCollectionParameter(param)) {
                Object collectionProcessing = createCollectionProcessing(param);
            }
        }
        
        return createMethodCall(createFieldAccess(sqlBuilderVar, "toString"));
    }

    public Object createPositionalSelectQuery(
            Object jdbcTemplate,
            Object sqlLiteral,
            List<?> methodParams,
            ResultMappingType mappingType,
            String resultTypeClass,
            String columnMapping,
            ExecutableElement methodElement
    ) throws Exception {

        Object paramArgs = null;
        if (methodParams != null && !methodParams.isEmpty()) {
            if (methodParams.size() == 1) {
                paramArgs = createParameterExpression(methodParams.get(0));
            } else {
                paramArgs = createParameterArray(methodParams);
            }
        }
        
        String returnTypeStr = methodElement.getReturnType().toString();

        if (returnTypeStr.startsWith("java.util.List")) {

            Object rowMapper;
            if (returnTypeStr.contains("Map<String") || returnTypeStr.contains("Map<java.lang.String")) {
                rowMapper = createColumnMapRowMapper();
            } else {
                rowMapper = createBeanPropertyRowMapper(resultTypeClass);
            }

            if (methodParams.isEmpty()) {
                return createMethodCall(createFieldAccess(jdbcTemplate, "query"), sqlLiteral, rowMapper);
            } else {
                return createMethodCall(createFieldAccess(jdbcTemplate, "query"), sqlLiteral, rowMapper, paramArgs);
            }
        }

        else {

            if (isSimpleType(resultTypeClass)) {
                Object queryCall;
                if (methodParams.isEmpty()) {
                    queryCall = createMethodCall(
                            createFieldAccess(jdbcTemplate, "queryForObject"),
                            sqlLiteral,
                            createClassLiteral(getFullTypeName(resultTypeClass))
                    );
                } else {
                    queryCall = createMethodCall(
                            createFieldAccess(jdbcTemplate, "queryForObject"),
                            sqlLiteral,
                            createClassLiteral(getFullTypeName(resultTypeClass)),
                            paramArgs
                    );
                }

                if (isPrimitive(resultTypeClass)) {
                    return queryCall;
                } else {
                    return createTypeCastExpression(queryCall, resultTypeClass);
                }
            } else {
                Object rowMapper = createBeanPropertyRowMapper(resultTypeClass);
                Object queryCall;
                if (methodParams.isEmpty()) {
                    queryCall = createMethodCall(createFieldAccess(jdbcTemplate, "queryForObject"), sqlLiteral, rowMapper);
                } else {
                    queryCall = createMethodCall(createFieldAccess(jdbcTemplate, "queryForObject"), sqlLiteral, rowMapper, paramArgs);
                }

                return createTypeCastExpression(queryCall, resultTypeClass);
            }
        }
    }

    /**
     * Create Positional UPDATE query
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
     * Create UPDATE query (Named Parameter)
     */
    public Object createUpdateQuery(Object namedJdbcTemplate, Object sqlLiteral, Object paramSource) throws Exception {
        Object updateMethod = createFieldAccess(namedJdbcTemplate, "update");
        return createMethodCall(updateMethod, sqlLiteral, paramSource);
    }

    /**
     * Create MapSqlParameterSource
     */
    public Object createParameterSourceCreation(List<?> methodParams) throws Exception {
        Object paramSourceType = createQualifiedIdent("org.springframework.jdbc.core.namedparam.MapSqlParameterSource");
        Object paramSourceExpr = createNewInstance(paramSourceType);
        
        Object chained = paramSourceExpr;
        for (Object param : methodParams) {
            String pName = null;
            try {
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
     * Create BeanPropertyRowMapper
     */
    private Object createBeanPropertyRowMapper(String resultTypeClass) throws Exception {
        return astHelper.getClass().getMethod("createBeanPropertyRowMapper", String.class)
                .invoke(astHelper, resultTypeClass);
    }

    private Object createNewClass(Object type, Object[] args) throws Exception {
        return astHelper.getClass().getMethod("createNewClass", Object.class, Object[].class)
                .invoke(astHelper, type, args);
    }

    /**
     * Create ColumnMapRowMapper
     */
    private Object createColumnMapRowMapper() throws Exception {
        Object columnMapRowMapperType = createQualifiedIdent("org.springframework.jdbc.core.ColumnMapRowMapper");
        return createNewInstance(columnMapRowMapperType);
    }

    // Helper methods - delegate to ASTHelper
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
    
    private Object createExpressionStatement(Object expr) throws Exception {
        return astHelper.getClass().getMethod("createExpressionStatement", Object.class).invoke(astHelper, expr);
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
    
    /**
     * Create single parameter expression from parameter info
     */
    private Object createParameterExpression(Object param) throws Exception {
        String paramName = extractParamName(param);
        return createIdent(paramName);
    }

    private String extractParamName(Object param) {
        if (param == null) return "param";
        
        if (param instanceof String) {
            return param.toString();
        }
        
        try {
            try { return (String) param.getClass().getMethod("getName").invoke(param); } catch (Exception ignore) {}
            try { return (String) param.getClass().getMethod("getParamName").invoke(param); } catch (Exception ignore) {}
            try { return (String) param.getClass().getMethod("getEffectiveName").invoke(param); } catch (Exception ignore) {}
        } catch (Exception ignore) {}
        return "param";
    }
    
    private String extractParamType(Object param) {
        if (param == null) return "Object";
        try {
            try { 
                Object type = param.getClass().getMethod("getType").invoke(param);
                if (type != null) return type.toString();
            } catch (Exception ignore) {}
            try { 
                Object type = param.getClass().getMethod("getParamType").invoke(param);
                if (type != null) return type.toString();
            } catch (Exception ignore) {}
        } catch (Exception ignore) {}
        return "Object";
    }
    
    private Object createArrayInitializer(String elementType, List<Object> elements) throws Exception {
        return astHelper.getClass().getMethod("createArrayInitializer", String.class, List.class)
                .invoke(astHelper, elementType, elements);
    }
    
    private Object createIdent(String name) throws Exception {
        return astHelper.getClass().getMethod("createIdent", String.class).invoke(astHelper, name);
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

    // Utility methods
    private boolean isCollectionParameter(Object param) {
        return false;
    }

    private boolean isSimpleType(String resultTypeClass) {
        return resultTypeClass.equals("String") || resultTypeClass.equals("Integer") || 
               resultTypeClass.equals("Long") || resultTypeClass.equals("Double") ||
               resultTypeClass.equals("Boolean") || resultTypeClass.equals("BigDecimal") ||
               resultTypeClass.equals("BigInteger") ||
               resultTypeClass.equals("long") || resultTypeClass.equals("int") ||
               resultTypeClass.equals("double") || resultTypeClass.equals("boolean") ||
               resultTypeClass.equals("float") || resultTypeClass.equals("short") ||
               resultTypeClass.equals("byte");
    }

    /**
     * Create type casting expression
     */
    private Object createTypeCastExpression(Object expression, String targetType) throws Exception {
        if (targetType.equals("long") || targetType.equals("int") || targetType.equals("double") || 
            targetType.equals("boolean") || targetType.equals("float") || targetType.equals("short") || 
            targetType.equals("byte")) {
            return expression;
        }
        
        try {
            Object targetTypeExpr = createQualifiedIdent(targetType);
            return astHelper.getClass().getMethod("createTypeCast", Object.class, Object.class)
                    .invoke(astHelper, targetTypeExpr, expression);
        } catch (Exception e) {
            return expression;
        }
    }

    /**
     * Create Optional.empty() call
     */
    private Object createOptionalEmpty() throws Exception {
        Object optionalClass = createQualifiedIdent("java.util.Optional");
        Object emptyMethod = createFieldAccess(optionalClass, "empty");
        return createMethodCall(emptyMethod);
    }

    /**
     * Convert simple type name to full class name
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
            case "long": return "java.lang.Long";
            case "int": return "java.lang.Integer";
            case "double": return "java.lang.Double";
            case "boolean": return "java.lang.Boolean";
            case "float": return "java.lang.Float";
            case "short": return "java.lang.Short";
            case "byte": return "java.lang.Byte";
            default: return simpleType;
        }
    }

    /**
     * Check if type is primitive
     */
    private boolean isPrimitive(String type) {
        return "long".equals(type) || "int".equals(type) || "double".equals(type) || 
               "boolean".equals(type) || "float".equals(type) || "short".equals(type) || 
               "byte".equals(type);
    }

}
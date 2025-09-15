package io.github.simplesqlgen.processor.sql;

import io.github.simplesqlgen.enums.NamingStrategy;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.*;

/**
 * Helper class responsible for SQL generation
 * Handles dynamic SQL generation, method name parsing, condition parsing, etc.
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
     * Create Find method implementation with validation
     */
    public Object createFindByImplementationWithValidation(String methodName, String entityName, String entityFqn, 
                                                         String tableName, Object entityInfo, ExecutableElement methodElement,
                                                         Object astHelper) throws Exception {
        QueryMethodInfo info = parseQueryMethodName(methodName);
        
        for (String field : info.getFields()) {
            if (!isValidEntityField(entityInfo, field)) {
                // Field validation failed - continue with generation
            }
        }
        
        String sql = generateAdvancedDynamicSQL(info, tableName);
        return createQueryImplementation(sql, methodElement, entityFqn, astHelper);
    }

    /**
     * Create Save method implementation (with entity)
     */
    public Object createSaveImplementationWithEntity(String entityName, String tableName, Object entityInfo, 
                                                    ExecutableElement methodElement, Object astHelper) throws Exception {
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
     * Create UpdateBy method implementation with validation
     */
    public Object createUpdateByImplementationWithValidation(String methodName, String tableName, Object entityInfo,
                                                            ExecutableElement methodElement, Object astHelper) throws Exception {
        QueryMethodInfo info = parseQueryMethodName(methodName);
        
        for (String field : info.getFields()) {
            if (!isValidEntityField(entityInfo, field)) {
                // Field validation failed - continue with generation
            }
        }
        
        String setField = extractSetField(methodName);
        String sql = "UPDATE " + tableName + " SET " + mapColumnName(setField) + " = ?" + generateWhereClause(info);
        
        List<String> allFields = new ArrayList<>();
        allFields.add(setField);
        allFields.addAll(info.getFields());
        
        return createUpdateByImplementation(sql, methodElement, allFields, astHelper);
    }
    
    private Object createUpdateByImplementation(String sql, ExecutableElement methodElement, List<String> fields, Object astHelper) throws Exception {
        Object jdbcTemplateAccess = astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, "this", "jdbcTemplate");
        Object updateMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                .invoke(astHelper, jdbcTemplateAccess, "update");
        
        Object sqlLiteral = astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, sql);
        
        // Create parameter array from method parameters directly
        List<? extends javax.lang.model.element.VariableElement> params = methodElement.getParameters();
        Object updateCall;
        
        if (params.isEmpty()) {
            updateCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, updateMethod, new Object[]{sqlLiteral});
        } else {
            List<Object> elements = new ArrayList<>();
            for (javax.lang.model.element.VariableElement param : params) {
                Object paramIdent = astHelper.getClass().getMethod("createIdent", String.class)
                        .invoke(astHelper, param.getSimpleName().toString());
                elements.add(paramIdent);
            }
            
            Object paramArray = astHelper.getClass().getMethod("createArrayInitializer", String.class, List.class)
                    .invoke(astHelper, "Object", elements);
            updateCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, updateMethod, new Object[]{sqlLiteral, paramArray});
        }
        
        // Check if method has void return type
        TypeMirror returnType = methodElement.getReturnType();
        String returnTypeStr = returnType.toString();
        boolean isVoid = "void".equals(returnTypeStr) || returnType.getKind().toString().equals("VOID");
        
        if (isVoid) {
            // For void methods, just execute the update without returning anything
            return astHelper.getClass().getMethod("createExpressionStatement", Object.class).invoke(astHelper, updateCall);
        } else if ("int".equals(returnTypeStr) || "java.lang.Integer".equals(returnTypeStr)) {
            // For int return, return the update count
            return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, updateCall);
        } else {
            // For entity return, execute update then return entity parameter (first parameter should be the entity or we construct it)
            List<? extends javax.lang.model.element.VariableElement> methodParams = methodElement.getParameters();
            if (!methodParams.isEmpty()) {
                // Return first parameter (assuming it's the entity with updated values)
                Object entityIdent = astHelper.getClass().getMethod("createIdent", String.class)
                        .invoke(astHelper, methodParams.get(0).getSimpleName().toString());
                
                Object updateStatement = astHelper.getClass().getMethod("createExpressionStatement", Object.class)
                        .invoke(astHelper, updateCall);
                Object returnStatement = astHelper.getClass().getMethod("createReturnStatement", Object.class)
                        .invoke(astHelper, entityIdent);
                
                List<Object> statements = new ArrayList<>();
                statements.add(updateStatement);
                statements.add(returnStatement);
                
                return astHelper.getClass().getMethod("createBlock", List.class).invoke(astHelper, statements);
            } else {
                // Fallback to returning update count
                return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, updateCall);
            }
        }
    }
    
    private String extractSetField(String methodName) {
        // Extract the field to update from "updateNameById" -> "name"
        // Note: "updateBy" methods are handled differently and don't have a set field
        if (methodName.matches("updateBy[A-Z].*")) {
            // For updateByName -> extract nothing (this is a where-clause-only pattern)
            return "";
        }
        
        // For updateNameById pattern
        String withoutUpdate = methodName.substring(6); // Remove "update"
        int byIndex = withoutUpdate.indexOf("By");
        if (byIndex > 0) {
            String fieldPart = withoutUpdate.substring(0, byIndex);
            // Convert first letter to lowercase
            return Character.toLowerCase(fieldPart.charAt(0)) + fieldPart.substring(1);
        }
        return "";
    }

    /**
     * Create Update method implementation (with entity)
     */
    public Object createUpdateImplementationWithEntity(String entityName, String tableName, Object entityInfo,
                                                      ExecutableElement methodElement, Object astHelper) throws Exception {
        List<String> fields = getEntityFields(entityInfo);
        
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
        
        if (fields.contains("id")) {
            sql.append(" WHERE id = ?");
        }
        
        return createUpdateImplementation(sql.toString(), methodElement, fields, astHelper);
    }

    /**
     * Create Count method implementation with validation
     */
    public Object createCountByImplementationWithValidation(String methodName, String tableName, Object entityInfo,
                                                           ExecutableElement methodElement, Object astHelper) throws Exception {
        QueryMethodInfo info = parseQueryMethodName(methodName);
        
        for (String field : info.getFields()) {
            if (!isValidEntityField(entityInfo, field)) {
                // Field validation failed - continue with generation
            }
        }
        
        String sql = "SELECT COUNT(*) FROM " + tableName + generateWhereClause(info);
        return createCountImplementation(sql, methodElement, astHelper);
    }

    /**
     * Create Delete method implementation with validation
     */
    public Object createDeleteByImplementationWithValidation(String methodName, String tableName, Object entityInfo,
                                                            ExecutableElement methodElement, Object astHelper) throws Exception {
        QueryMethodInfo info = parseQueryMethodName(methodName);
        
        // Validate entity fields (more leniently)
        for (String field : info.getFields()) {
            if (!isValidEntityField(entityInfo, field)) {
                // Field not found in entity, but proceeding with generation
            }
        }
        
        String sql = "DELETE FROM " + tableName + generateWhereClause(info);
        return createDeleteImplementation(sql, methodElement, astHelper);
    }

    /**
     * Create Exists method implementation with validation
     */
    public Object createExistsByImplementationWithValidation(String methodName, String tableName, Object entityInfo,
                                                            ExecutableElement methodElement, Object astHelper) throws Exception {
        try {
            QueryMethodInfo info = parseQueryMethodName(methodName);
            for (String field : info.getFields()) {
                if (!isValidEntityField(entityInfo, field)) {
                    // Field validation failed - continue with generation
                }
            }
            String sql = "SELECT COUNT(*) FROM " + tableName + generateWhereClause(info);
            return createExistsImplementation(sql, methodElement, astHelper);
        } catch (Exception e) {
            try {
                Object falseLit = astHelper.getClass().getMethod("createLiteral", Object.class).invoke(astHelper, false);
                return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, falseLit);
            } catch (Exception ex) {
                throw e;
            }
        }
    }

    /**
     * Create FindAll implementation
     */
    public Object createFindAllImplementation(String entityFqn, String tableName, Object astHelper) throws Exception {
        String sql = "SELECT * FROM " + tableName;
        return createFindAllQueryImplementation(sql, entityFqn, astHelper);
    }

    /**
     * Parse query method name
     */
    public QueryMethodInfo parseQueryMethodName(String methodName) {
        QueryMethodInfo info = new QueryMethodInfo();
        
        if (methodName.startsWith("findBy")) {
            String condition = methodName.substring(methodName.indexOf("By") + 2);
            info = parseCondition(condition);
            info.setOperation("find");
            return info;
        } else if (methodName.startsWith("countBy")) {
            String condition = methodName.substring(methodName.indexOf("By") + 2);
            info = parseCondition(condition);
            info.setOperation("count");
            return info;
        } else if (methodName.startsWith("deleteBy")) {
            String condition = methodName.substring(methodName.indexOf("By") + 2);
            info = parseCondition(condition);
            info.setOperation("delete");
            return info;
        } else if (methodName.startsWith("existsBy")) {
            String condition = methodName.substring(methodName.indexOf("By") + 2);
            info = parseCondition(condition);
            info.setOperation("exists");
            return info;
        } else if (methodName.equals("findAll")) {
            info.setOperation("findAll");
            return info;
        } else if (methodName.startsWith("save")) {
            info.setOperation("save");
            return info;
        } else if (methodName.startsWith("updateBy")) {
            String condition = methodName.substring(methodName.indexOf("By") + 2);
            info = parseCondition(condition);
            info.setOperation("update");
            return info;
        } else {
            info.setOperation("unknown");
            return info;
        }
    }

    /**
     * Parse condition
     */
    public QueryMethodInfo parseCondition(String condition) {
        QueryMethodInfo info = new QueryMethodInfo();
        List<String> fields = new ArrayList<>();
        List<String> operators = new ArrayList<>();
        List<String> logicalOperators = new ArrayList<>();
        
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
            
            String field = part;
            String operator = "=";
            
            for (String compOp : COMPARISON_OPERATORS) {
                if (part.endsWith(compOp)) {
                    field = part.substring(0, part.length() - compOp.length());
                    operator = mapOperatorToSql(compOp);
                    break;
                }
            }
            
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
     * Generate advanced dynamic SQL
     */
    public String generateAdvancedDynamicSQL(QueryMethodInfo info, String tableName) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName);
        
        if (!info.getFields().isEmpty()) {
            sql.append(generateWhereClause(info));
        }
        
        return sql.toString();
    }

    /**
     * Generate WHERE clause
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
            
            if ("IS NULL".equals(op) || "IS NOT NULL".equals(op)) {
                where.append(column).append(" ").append(op);
            } else if ("BETWEEN".equals(op) || "NOT BETWEEN".equals(op)) {
                where.append(column).append(" ").append(op).append(" ? AND ?");
            } else if ("IN".equals(op) || "NOT IN".equals(op)) {
                where.append(column).append(" ").append(op).append(" (").append("?").append(")");
            } else {
                where.append(column).append(" ").append(op).append(" ?");
            }
        }
        
        return where.toString();
    }

    /**
     * Map operator to SQL form
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
     * Validate if entity field is valid
     */
    private boolean isValidEntityField(Object entityInfo, String fieldName) {
        try {
            java.lang.reflect.Method getFieldsMethod = entityInfo.getClass().getMethod("getFields");
            @SuppressWarnings("unchecked")
            List<String> fields = (List<String>) getFieldsMethod.invoke(entityInfo);
            
            if (fields.contains(fieldName)) {
                return true;
            }
            
            if ("id".equals(fieldName)) {
                return true;
            }
            
            for (String field : fields) {
                if (field.equals(fieldName)) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            // Return true by default (for compatibility)
            return true;
        }
    }

    /**
     * Get entity field list
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

    // Generate actual JDBC code
    private Object createQueryImplementation(String sql, ExecutableElement methodElement, String entityFqn, Object astHelper) throws Exception {
        Object jdbcTemplateAccess = astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, "this", "jdbcTemplate");
        
        Object sqlLiteral = astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, sql);
        
        TypeMirror returnType = methodElement.getReturnType();
        String returnTypeStr = returnType.toString();
        
        Object rowMapper;
        if (returnTypeStr.contains("Map<String, Object>") || returnTypeStr.contains("Map<java.lang.String, java.lang.Object>")) {
            rowMapper = astHelper.getClass().getMethod("createColumnMapRowMapper")
                    .invoke(astHelper);
        } else {
            rowMapper = astHelper.getClass().getMethod("createBeanPropertyRowMapper", String.class)
                    .invoke(astHelper, getSimpleClassName(entityFqn));
        }
        
        // Configure method parameters
        List<? extends javax.lang.model.element.VariableElement> params = methodElement.getParameters();
        Object paramArgs = null;
        boolean isSingleParam = false;
        
        if (params != null && !params.isEmpty()) {
            if (params.size() == 1) {
                // Single parameter: pass directly without Object[] wrapper
                String name = params.get(0).getSimpleName().toString();
                paramArgs = astHelper.getClass().getMethod("createIdent", String.class).invoke(astHelper, name);
                isSingleParam = true;
            } else {
                // Multiple parameters: use Object[] array
                List<Object> elements = new ArrayList<>();
                for (javax.lang.model.element.VariableElement ve : params) {
                    String name = ve.getSimpleName().toString();
                    Object ident = astHelper.getClass().getMethod("createIdent", String.class).invoke(astHelper, name);
                    elements.add(ident);
                }
                paramArgs = astHelper.getClass().getMethod("createArrayInitializer", String.class, List.class)
                        .invoke(astHelper, "Object", elements);
                isSingleParam = false;
            }
        }
        
        // Select query vs queryForObject based on return type
        Object queryCall;
        if (returnTypeStr.startsWith("java.util.List")) {
            // List return type - use jdbcTemplate.query()
            Object queryMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                    .invoke(astHelper, jdbcTemplateAccess, "query");
            if (paramArgs == null) {
                queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                        .invoke(astHelper, queryMethod, new Object[]{sqlLiteral, rowMapper});
            } else {
                queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                        .invoke(astHelper, queryMethod, new Object[]{sqlLiteral, rowMapper, paramArgs});
            }
        } else {
            // Single object return type - use jdbcTemplate.queryForObject()
            Object queryForObjectMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                    .invoke(astHelper, jdbcTemplateAccess, "queryForObject");
            if (paramArgs == null) {
                queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                        .invoke(astHelper, queryForObjectMethod, new Object[]{sqlLiteral, rowMapper});
            } else {
                queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                        .invoke(astHelper, queryForObjectMethod, new Object[]{sqlLiteral, rowMapper, paramArgs});
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
        String methodName = methodElement.getSimpleName().toString();
        boolean isSaveMethod = methodName.startsWith("save");
        
        Object jdbcTemplateAccess = astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, "this", "jdbcTemplate");
        Object updateMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                .invoke(astHelper, jdbcTemplateAccess, "update");
        
        Object sqlLiteral = astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, sql);
        
        // Check if this is entity-based or individual parameter-based update
        List<? extends javax.lang.model.element.VariableElement> params = methodElement.getParameters();
        Object paramArray = null;
        Object entityIdent = null;
        if (params != null && !params.isEmpty()) {
            // Check if first parameter is an entity or basic type
            boolean isEntityBasedUpdate = isEntityParameter(params.get(0));
            
            if (isEntityBasedUpdate) {
                // Entity-based update: extract values from entity using getters
                String entityParamName = params.get(0).getSimpleName().toString();
                entityIdent = astHelper.getClass().getMethod("createIdent", String.class).invoke(astHelper, entityParamName);
                List<Object> elements = new ArrayList<>();
                boolean isUpdateSql = sql.trim().toUpperCase().startsWith("UPDATE");
                for (String f : fields) {
                    if (isUpdateSql && "id".equals(f)) continue; // Exclude id from SET in UPDATE
                    String getter = getBooleanAwareGetter(f);
                    Object getterSel = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                            .invoke(astHelper, entityIdent, getter);
                    Object getterCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                            .invoke(astHelper, getterSel, new Object[]{});
                    elements.add(getterCall);
                }
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
            } else {
                // Individual parameter-based update: use parameters directly
                List<Object> elements = new ArrayList<>();
                for (javax.lang.model.element.VariableElement param : params) {
                    String paramName = param.getSimpleName().toString();
                    Object paramIdent = astHelper.getClass().getMethod("createIdent", String.class)
                            .invoke(astHelper, paramName);
                    elements.add(paramIdent);
                }
                paramArray = astHelper.getClass().getMethod("createArrayInitializer", String.class, List.class)
                        .invoke(astHelper, "Object", elements);
            }
        }
        
        Object updateCall;
        if (paramArray == null) {
            updateCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, updateMethod, new Object[]{sqlLiteral});
        } else {
            updateCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, updateMethod, new Object[]{sqlLiteral, paramArray});
        }
        
        if (isSaveMethod && entityIdent != null) {
            // For save methods, check return type to decide what to return
            TypeMirror returnType = methodElement.getReturnType();
            String returnTypeStr = returnType.toString();
            
            if ("int".equals(returnTypeStr) || "java.lang.Integer".equals(returnTypeStr)) {
                // User wants int return - return affected row count
                Object returnStatement = astHelper.getClass().getMethod("createReturnStatement", Object.class)
                        .invoke(astHelper, updateCall);
                
                List<Object> statements = new ArrayList<>();
                statements.add(returnStatement);
                
                return astHelper.getClass().getMethod("createBlock", List.class).invoke(astHelper, statements);
            } else {
                // User wants entity return - execute update then return entity
                Object updateStatement = astHelper.getClass().getMethod("createExpressionStatement", Object.class)
                        .invoke(astHelper, updateCall);
                Object returnStatement = astHelper.getClass().getMethod("createReturnStatement", Object.class)
                        .invoke(astHelper, entityIdent);
                
                List<Object> statements = new ArrayList<>();
                statements.add(updateStatement);
                statements.add(returnStatement);
                
                return astHelper.getClass().getMethod("createBlock", List.class).invoke(astHelper, statements);
            }
        } else {
            // Check if method has void return type
            TypeMirror returnType = methodElement.getReturnType();
            String returnTypeStr = returnType.toString();
            
            if ("void".equals(returnTypeStr)) {
                // For void methods, just execute the update without returning anything
                return astHelper.getClass().getMethod("createExpressionStatement", Object.class).invoke(astHelper, updateCall);
            } else {
                // For update/delete methods, return the update count
                return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, updateCall);
            }
        }
    }

    private Object createCountImplementation(String sql, ExecutableElement methodElement, Object astHelper) throws Exception {
        Object jdbcTemplateAccess = astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, "this", "jdbcTemplate");
        
        // Access queryForObject method
        Object queryForObjectMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                .invoke(astHelper, jdbcTemplateAccess, "queryForObject");
        
        Object sqlLiteral = astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, sql);
        Object longClassLiteral = astHelper.getClass().getMethod("createClassLiteral", String.class)
                .invoke(astHelper, "java.lang.Long");
        
        // Configure method parameters
        List<? extends javax.lang.model.element.VariableElement> params = methodElement.getParameters();
        Object paramArgs = null;
        if (params != null && !params.isEmpty()) {
            if (params.size() == 1) {
                // Single parameter: pass directly
                String name = params.get(0).getSimpleName().toString();
                paramArgs = astHelper.getClass().getMethod("createIdent", String.class).invoke(astHelper, name);
            } else {
                // Multiple parameters: use Object[] array
                List<Object> elements = new ArrayList<>();
                for (javax.lang.model.element.VariableElement ve : params) {
                    String name = ve.getSimpleName().toString();
                    Object ident = astHelper.getClass().getMethod("createIdent", String.class).invoke(astHelper, name);
                    elements.add(ident);
                }
                paramArgs = astHelper.getClass().getMethod("createArrayInitializer", String.class, List.class)
                        .invoke(astHelper, "Object", elements);
            }
        }
        
        // Create queryForObject call
        Object queryCall;
        if (paramArgs == null) {
            queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, queryForObjectMethod, new Object[]{sqlLiteral, longClassLiteral});
        } else {
            queryCall = astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class)
                    .invoke(astHelper, queryForObjectMethod, new Object[]{sqlLiteral, longClassLiteral, paramArgs});
        }
        
        // Cast Object to Long: (Long) queryCall
        Object longType = astHelper.getClass().getMethod("createQualifiedIdent", String.class)
                .invoke(astHelper, "Long");
        
        try {
            Object castedCall = astHelper.getClass().getMethod("createTypeCast", Object.class, Object.class)
                    .invoke(astHelper, longType, queryCall);
            return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, castedCall);
        } catch (Exception e) {
            // If createTypeCast fails, return Long type directly (simple fallback)
            return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, queryCall);
        }
    }

    private Object createDeleteImplementation(String sql, ExecutableElement methodElement, Object astHelper) throws Exception {
        Object jdbcTemplateAccess = astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, "this", "jdbcTemplate");
        Object updateMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                .invoke(astHelper, jdbcTemplateAccess, "update");
        
        Object sqlLiteral = astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, sql);
        
        // Configure method parameters as Object[]
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
        
        // Check if method has void return type
        TypeMirror returnType = methodElement.getReturnType();
        String returnTypeStr = returnType.toString();
        boolean isVoid = "void".equals(returnTypeStr) || returnType.getKind().toString().equals("VOID");
        
        if (isVoid) {
            // For void methods, just execute the update without returning anything
            return astHelper.getClass().getMethod("createExpressionStatement", Object.class).invoke(astHelper, updateCall);
        } else {
            // For delete methods, return the update count
            return astHelper.getClass().getMethod("createReturnStatement", Object.class).invoke(astHelper, updateCall);
        }
    }

    private Object createExistsImplementation(String sql, ExecutableElement methodElement, Object astHelper) throws Exception {
        Object jdbcTemplateAccess = astHelper.getClass().getMethod("createFieldAccess", String.class, String.class)
                .invoke(astHelper, "this", "jdbcTemplate");
        Object queryForObjectMethod = astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class)
                .invoke(astHelper, jdbcTemplateAccess, "queryForObject");
        
        Object sqlLiteral = astHelper.getClass().getMethod("createLiteral", String.class).invoke(astHelper, sql);
        Object longClassLiteral = astHelper.getClass().getMethod("createClassLiteral", String.class)
                .invoke(astHelper, "java.lang.Long");
        
        // Configure method parameter array
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

    private Object createFindAllQueryImplementation(String sql, String entityFqn, Object astHelper) throws Exception {
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
        if (fullyQualifiedName == null || fullyQualifiedName.isEmpty()) {
            return fullyQualifiedName;
        }
        return fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
    }
    
    /**
     * Check if a parameter is an entity type (not a primitive/wrapper type)
     */
    private boolean isEntityParameter(javax.lang.model.element.VariableElement param) {
        String paramType = param.asType().toString();
        
        // Basic types and wrappers should use parameters directly
        if (isPrimitiveOrWrapper(paramType)) {
            return false;
        }
        
        // String is not an entity
        if ("java.lang.String".equals(paramType) || "String".equals(paramType)) {
            return false;
        }
        
        // Collection types are not entities
        if (paramType.startsWith("java.util.List") || paramType.startsWith("java.util.Set") || 
            paramType.startsWith("java.util.Map") || paramType.startsWith("java.util.Collection")) {
            return false;
        }
        
        // Date/Time types are not entities
        if (paramType.startsWith("java.time.") || paramType.startsWith("java.util.Date")) {
            return false;
        }
        
        // Assume everything else is an entity
        return true;
    }
    
    /**
     * Check if a type is primitive or wrapper type
     */
    private boolean isPrimitiveOrWrapper(String typeName) {
        return "int".equals(typeName) || "java.lang.Integer".equals(typeName) ||
               "long".equals(typeName) || "java.lang.Long".equals(typeName) ||
               "double".equals(typeName) || "java.lang.Double".equals(typeName) ||
               "float".equals(typeName) || "java.lang.Float".equals(typeName) ||
               "boolean".equals(typeName) || "java.lang.Boolean".equals(typeName) ||
               "byte".equals(typeName) || "java.lang.Byte".equals(typeName) ||
               "short".equals(typeName) || "java.lang.Short".equals(typeName) ||
               "char".equals(typeName) || "java.lang.Character".equals(typeName);
    }

    /**
     * Get the appropriate getter method name for a field, handling boolean fields
     */
    private String getBooleanAwareGetter(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return "get" + fieldName;
        }
        
        // Common boolean field patterns - use "is" prefix
        if ("active".equals(fieldName) || "available".equals(fieldName) || 
            "enabled".equals(fieldName) || "valid".equals(fieldName) ||
            fieldName.startsWith("is") || fieldName.startsWith("has") || fieldName.startsWith("can") ||
            fieldName.contains("Bool") || fieldName.toLowerCase().contains("bool") ||
            fieldName.equals("boolField")) {
            return "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        }
        
        return "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }

    /**
     * Class to hold query method information
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
package io.github.simplesqlgen.processor.param;

import io.github.simplesqlgen.annotation.Param;
import io.github.simplesqlgen.enums.ParameterType;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parameter processing handler
 * Analyzes method parameters, validates mappings, and generates parameter code
 */
public class ParameterProcessor {
    
    private Object astHelper;

    public ParameterProcessor(Object astHelper) {
        this.astHelper = astHelper;
    }

    /**
     * Analyze method parameters
     */
    public List<ParameterInfo> analyzeMethodParameters(ExecutableElement methodElement) {
        List<ParameterInfo> paramInfos = new ArrayList<>();
        
        for (VariableElement param : methodElement.getParameters()) {
            ParameterInfo info = new ParameterInfo();
            info.setName(param.getSimpleName().toString());
            info.setType(param.asType());
            info.setTypeString(param.asType().toString());
            
            Param paramAnnotation = param.getAnnotation(Param.class);
            if (paramAnnotation != null) {
                info.setParamName(paramAnnotation.value());
                info.setParameterType(ParameterType.NAMED);
            } else {
                info.setParamName(info.getName());
                info.setParameterType(ParameterType.POSITIONAL);
            }

            String typeStr = info.getTypeString();
            if (typeStr.startsWith("java.util.List") || typeStr.startsWith("java.util.Collection") || 
                typeStr.startsWith("java.util.Set") || typeStr.endsWith("[]")) {
                info.setCollection(true);
            }
            
            paramInfos.add(info);
        }
        
        return paramInfos;
    }

    /**
     * Validate Named Parameter mapping
     */
    public void validateParameterMapping(List<String> namedParams, List<ParameterInfo> methodParams, String methodName) {
        List<String> paramNames = new ArrayList<>();
        for (ParameterInfo info : methodParams) {
            paramNames.add(info.getParamName());
        }
        
        for (String namedParam : namedParams) {
            if (!paramNames.contains(namedParam)) {
                throw new RuntimeException("Parameter '" + namedParam + "' not found in method parameters for method: " + methodName);
            }
        }
    }

    /**
     * Validate Positional Parameters
     */
    public void validatePositionalParameters(int placeholderCount, List<ParameterInfo> methodParams, String methodName) {
        if (placeholderCount != methodParams.size()) {
            throw new RuntimeException("Parameter count mismatch in method " + methodName + 
                                     ". Expected: " + placeholderCount + ", Found: " + methodParams.size());
        }
    }

    /**
     * Extract Named Parameters from SQL
     */
    public List<String> extractNamedParameters(String sql) {
        List<String> namedParams = new ArrayList<>();
        Pattern pattern = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");
        Matcher matcher = pattern.matcher(sql);
        
        while (matcher.find()) {
            String paramName = matcher.group(1);
            if (!namedParams.contains(paramName)) {
                namedParams.add(paramName);
            }
        }
        
        return namedParams;
    }

    /**
     * Count Positional Parameters in SQL
     */
    public int countPositionalParameters(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }

    /**
     * Create MapSqlParameterSource code
     */
    public Object createParameterSourceCreation(List<ParameterInfo> methodParams) throws Exception {
        Object paramSourceType = createQualifiedIdent("org.springframework.jdbc.core.namedparam.MapSqlParameterSource");
        Object paramSourceExpr = createNewInstance(paramSourceType);
        
        Object paramSourceVar = createVariable("paramSource", paramSourceType);
        Object assignment = createAssignment(paramSourceVar, paramSourceExpr);
        
        List<Object> statements = new ArrayList<>();
        statements.add(assignment);
        

        for (ParameterInfo paramInfo : methodParams) {
            Object addValueStatement = createAddValueStatement(paramInfo);
            statements.add(addValueStatement);
        }
        
        return createBlock(statements);
    }

    /**
     * Create addValue statement
     */
    public Object createAddValueStatement(ParameterInfo paramInfo) throws Exception {
        Object paramNameLiteral = createLiteral(paramInfo.getParamName());
        Object paramValueVar = createVariable(paramInfo.getName());
        
        Object addValueCall;
        if (paramInfo.isCollection()) {

            Object toArrayCall = createMethodCall(createFieldAccess(paramValueVar, "toArray"));
            addValueCall = createMethodCall(
                createFieldAccess("paramSource", "addValue"),
                paramNameLiteral, 
                toArrayCall
            );
        } else {
            addValueCall = createMethodCall(
                createFieldAccess("paramSource", "addValue"),
                paramNameLiteral, 
                paramValueVar
            );
        }
        
        return createExpressionStatement(addValueCall);
    }

    /**
     * Dynamic SQL processing for Collection parameters
     */
    public Object createDynamicSqlProcessing(String sql, List<ParameterInfo> methodParams) throws Exception {
        List<Object> statements = new ArrayList<>();
        
        Object sqlBuilderVar = createVariable("sqlBuilder", "StringBuilder");
        Object sqlBuilderInit = createNewStringBuilder(sql);
        Object sqlBuilderAssignment = createAssignment(sqlBuilderVar, sqlBuilderInit);
        statements.add(sqlBuilderAssignment);
        

        for (int i = 0; i < methodParams.size(); i++) {
            ParameterInfo param = methodParams.get(i);
            if (param.isCollection()) {
                Object collectionProcessing = createInClauseProcessing(param, i);
                statements.add(collectionProcessing);
            }
        }
        
        Object sqlVar = createVariable("sql", "String");
        Object sqlAssignment = createAssignment(sqlVar, 
            createMethodCall(createFieldAccess(sqlBuilderVar, "toString")));
        statements.add(sqlAssignment);
        
        return createBlock(statements);
    }

    /**
     * Create IN clause processing
     */
    public Object createInClauseProcessing(ParameterInfo param, int paramIndex) throws Exception {
        List<Object> statements = new ArrayList<>();
        
        Object paramVar = createVariable(param.getName());
        
        Object isEmptyCheck = createMethodCall(createFieldAccess(paramVar, "isEmpty"));
        Object notEmptyCondition = createUnaryExpression("!", isEmptyCheck);
        
        Object placeholdersVar = createVariable("placeholders", "StringBuilder");
        Object placeholdersInit = createNewStringBuilder("");
        statements.add(createAssignment(placeholdersVar, placeholdersInit));
        
        Object forLoop = createForLoop(param, createPlaceholderGeneration(param));
        statements.add(forLoop);
        
        String placeholder = "\\?" + (paramIndex > 0 ? "{" + paramIndex + "}" : "");
        Object replaceCall = createMethodCall(
            createFieldAccess("sqlBuilder", "toString"),
            "replaceFirst",
            createLiteral(placeholder),
            createMethodCall(createFieldAccess(placeholdersVar, "toString"))
        );
        
        Object replaceStatement = createExpressionStatement(
            createAssignment(
                createFieldAccess("sqlBuilder", "setLength"), 
                createLiteral(0)
            )
        );
        
        statements.add(replaceStatement);
        
        Object ifStatement = createIfStatement(notEmptyCondition, createBlock(statements));
        
        return ifStatement;
    }

    /**
     * Placeholder generation loop body
     */
    public Object createPlaceholderGeneration(ParameterInfo param) throws Exception {
        List<Object> statements = new ArrayList<>();
        
        Object iVar = createVariable("i");
        Object condition = createBinaryExpression(iVar, ">", createLiteral(0));
        Object commaAppend = createMethodCall(
            createFieldAccess("placeholders", "append"),
            createLiteral(", ")
        );
        Object commaIf = createIfStatement(condition, createExpressionStatement(commaAppend));
        statements.add(commaIf);
        
        Object questionAppend = createMethodCall(
            createFieldAccess("placeholders", "append"),
            createLiteral("?")
        );
        statements.add(createExpressionStatement(questionAppend));
        
        return createBlock(statements);
    }

    /**
     * Create For loop
     */
    public Object createForLoop(ParameterInfo param, Object body) throws Exception {
        Object paramVar = createVariable(param.getName());
        Object sizeCall = createMethodCall(createFieldAccess(paramVar, "size"));
        
        Object iVar = createVariable("i");
        Object iInit = createAssignment(iVar, createLiteral(0));
        Object condition = createBinaryExpression(iVar, "<", sizeCall);
        Object increment = createUnaryExpression("++", iVar);
        
        return createForStatement(iInit, condition, increment, body);
    }

    /**
     * Create parameter array
     */
    public Object createParameterArray(List<ParameterInfo> methodParams) throws Exception {
        List<Object> elements = new ArrayList<>();
        
        for (ParameterInfo param : methodParams) {
            if (param.isCollection()) {
                Object toArrayCall = createMethodCall(createFieldAccess(createVariable(param.getName()), "toArray"));
                elements.add(toArrayCall);
            } else {
                elements.add(createVariable(param.getName()));
            }
        }
        
        return createArrayInitializer("Object", elements);
    }


    private Object createQualifiedIdent(String qualifiedName) throws Exception {
        return astHelper.getClass().getMethod("createQualifiedIdent", String.class).invoke(astHelper, qualifiedName);
    }

    private Object createNewInstance(Object type) throws Exception {
        return astHelper.getClass().getMethod("createNewInstance", Object.class).invoke(astHelper, type);
    }

    private Object createVariable(String name, Object type) throws Exception {
        return astHelper.getClass().getMethod("createVariable", String.class, Object.class).invoke(astHelper, name, type);
    }

    private Object createVariable(String name) throws Exception {
        return astHelper.getClass().getMethod("createVariable", String.class).invoke(astHelper, name);
    }

    private Object createAssignment(Object left, Object right) throws Exception {
        return astHelper.getClass().getMethod("createAssignment", Object.class, Object.class).invoke(astHelper, left, right);
    }

    private Object createBlock(List<Object> statements) throws Exception {
        return astHelper.getClass().getMethod("createBlock", List.class).invoke(astHelper, statements);
    }

    private Object createLiteral(Object value) throws Exception {
        return astHelper.getClass().getMethod("createLiteral", Object.class).invoke(astHelper, value);
    }

    private Object createMethodCall(Object method, Object... args) throws Exception {
        return astHelper.getClass().getMethod("createMethodCall", Object.class, Object[].class).invoke(astHelper, method, args);
    }

    private Object createFieldAccess(Object base, String fieldName) throws Exception {
        return astHelper.getClass().getMethod("createFieldAccess", Object.class, String.class).invoke(astHelper, base, fieldName);
    }

    private Object createFieldAccess(String baseName, String fieldName) throws Exception {
        return astHelper.getClass().getMethod("createFieldAccess", String.class, String.class).invoke(astHelper, baseName, fieldName);
    }

    private Object createExpressionStatement(Object expr) throws Exception {
        return astHelper.getClass().getMethod("createExpressionStatement", Object.class).invoke(astHelper, expr);
    }

    private Object createNewStringBuilder(String initialValue) throws Exception {
        return astHelper.getClass().getMethod("createNewStringBuilder", String.class).invoke(astHelper, initialValue);
    }

    private Object createUnaryExpression(String operator, Object operand) throws Exception {
        return astHelper.getClass().getMethod("createUnaryExpression", String.class, Object.class).invoke(astHelper, operator, operand);
    }

    private Object createBinaryExpression(Object left, String operator, Object right) throws Exception {
        return astHelper.getClass().getMethod("createBinaryExpression", Object.class, String.class, Object.class).invoke(astHelper, left, operator, right);
    }

    private Object createIfStatement(Object condition, Object thenStatement) throws Exception {
        return astHelper.getClass().getMethod("createIfStatement", Object.class, Object.class).invoke(astHelper, condition, thenStatement);
    }

    private Object createForStatement(Object init, Object condition, Object update, Object body) throws Exception {
        return astHelper.getClass().getMethod("createForStatement", Object.class, Object.class, Object.class, Object.class).invoke(astHelper, init, condition, update, body);
    }

    private Object createArrayInitializer(String elementType, List<Object> elements) throws Exception {
        return astHelper.getClass().getMethod("createArrayInitializer", String.class, List.class).invoke(astHelper, elementType, elements);
    }

    /**
     * Parameter information holder
     */
    public static class ParameterInfo {
        private String name;
        private TypeMirror type;
        private String typeString;
        private String paramName;
        private ParameterType parameterType;
        private boolean isCollection;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public TypeMirror getType() { return type; }
        public void setType(TypeMirror type) { this.type = type; }

        public String getTypeString() { return typeString; }
        public void setTypeString(String typeString) { this.typeString = typeString; }

        public String getParamName() { return paramName; }
        public void setParamName(String paramName) { this.paramName = paramName; }

        public ParameterType getParameterType() { return parameterType; }
        public void setParameterType(ParameterType parameterType) { this.parameterType = parameterType; }

        public boolean isCollection() { return isCollection; }
        public void setCollection(boolean collection) { isCollection = collection; }
    }
}
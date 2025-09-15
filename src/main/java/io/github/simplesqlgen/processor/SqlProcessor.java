package io.github.simplesqlgen.processor;

import com.google.auto.service.AutoService;
import io.github.simplesqlgen.annotation.NativeQuery;
import io.github.simplesqlgen.annotation.SqlGenerator;
import io.github.simplesqlgen.enums.NamingStrategy;
import io.github.simplesqlgen.permit.Permit;
import io.github.simplesqlgen.processor.ast.ASTHelper;
import io.github.simplesqlgen.processor.param.ParameterProcessor;
import io.github.simplesqlgen.processor.param.ParameterProcessor.ParameterInfo;
import io.github.simplesqlgen.processor.query.QueryExecutor;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SqlProcessor - Main annotation processor class
 * Delegates to separate helper classes for clean and clear structure
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({
        "io.github.simplesqlgen.annotation.SqlGenerator",
        "io.github.simplesqlgen.annotation.NativeQuery"})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class SqlProcessor extends AbstractProcessor {

    // Helper classes
    private ASTHelper astHelper;
    private io.github.simplesqlgen.processor.sql.SqlGenerator sqlGenerator;
    private QueryExecutor queryExecutor;
    private ParameterProcessor parameterProcessor;

    // Logging flags for AST processor (default: minimal output)
    private static final boolean AST_VERBOSE = Boolean.parseBoolean(System.getProperty("rdb.ast.verbose", "false"));
    private static final boolean AST_DEBUG = Boolean.parseBoolean(System.getProperty("rdb.ast.debug", "false"));

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        Permit.permitAllJavacPackages();

        try {
            initializeHelpers(processingEnv);
        } catch (Exception e) {
            logError("Initialization failed: " + e.getMessage());
            throw new RuntimeException("SqlProcessor initialization failed", e);
        }
    }

    private void initializeHelpers(ProcessingEnvironment processingEnv) {
        try {
            astHelper = new ASTHelper();
            astHelper.initialize(processingEnv);

            if (!astHelper.isASTAvailable()) {
                throw new IllegalStateException("AST-based processing is not available");
            }

            sqlGenerator = new io.github.simplesqlgen.processor.sql.SqlGenerator();
            queryExecutor = new QueryExecutor(astHelper);
            parameterProcessor = new ParameterProcessor(astHelper);
        } catch (Exception e) {
            throw new RuntimeException("Helper initialization failed", e);
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        try {
            processAnnotatedClasses(roundEnv);
            return true;
        } catch (Exception e) {
            logError("Processing error occurred: " + e.getMessage());
            return false;
        }
    }

    private void processAnnotatedClasses(RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(SqlGenerator.class)) {
            if (element instanceof TypeElement) {
                TypeElement te = (TypeElement) element;
                processSqlGeneratorClass(te);
            }
        }
    }

    private void processSqlGeneratorClass(TypeElement classElement) {
        try {
            ClassProcessingContext context = createProcessingContext(classElement);

            try { sqlGenerator.setNamingStrategy(context.getNamingStrategy()); } catch (Exception ignore) { }

            validateEntityInfo(context);
            processClassWithAST(context);

        } catch (Exception e) {
            logError("SqlGenerator class processing failed: " + e.getMessage());
            if (AST_DEBUG) {
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                e.printStackTrace(pw);
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, sw.toString());
            }
        }
    }

    private ClassProcessingContext createProcessingContext(TypeElement classElement) {
        SqlGenerator annotation = classElement.getAnnotation(SqlGenerator.class);

        boolean nativeOnly = false;
        try {
            nativeOnly = annotation.nativeQueryOnly();
        } catch (Exception ignore) { }

        if (!nativeOnly) {
            nativeOnly = isVoidEntity(annotation);
        }

        TypeMirror entityType = getEntityType(annotation);
        String entityName = entityType != null ? getSimpleName(entityType) : "void";
        NamingStrategy namingStrategy = annotation.namingStrategy();
        String tableName;
        if (annotation.tableName().isEmpty()) {
            tableName = applyNamingStrategy(entityName, namingStrategy);
        } else {
            tableName = annotation.tableName();
        }

        EntityInfo entityInfo = nativeOnly ? new EntityInfo() : analyzeEntity(entityType);

        return new ClassProcessingContext(classElement, entityType, entityName, tableName, entityInfo, namingStrategy, nativeOnly);
    }

    private void validateEntityInfo(ClassProcessingContext context) {
        if (context.isNativeQueryOnly()) {
            return;
        }
        if (context.getEntityInfo().getFields().isEmpty()) {
            throw new IllegalStateException("Entity field analysis failed or no fields found: " + context.getEntityName());
        }
    }

    private void processClassWithAST(ClassProcessingContext context) throws Exception {
        Object treePath = astHelper.getTreePath(context.getClassElement());
        if (treePath == null) {
            throw new IllegalStateException("Cannot get TreePath: " + context.getClassName());
        }

        Object classDecl = astHelper.getClassDecl(treePath);

        astHelper.injectAutowiredFields(classDecl);
        transformClassMethods(classDecl, context);
    }

    private int transformClassMethods(Object classDecl, ClassProcessingContext context) throws Exception {
        try {
            java.lang.reflect.Field defsField = classDecl.getClass().getDeclaredField("defs");
            defsField.setAccessible(true);
            Object membersList = defsField.get(classDecl);

            TransformResult result = createTransformedMembersList(membersList, context);
            defsField.set(classDecl, result.newList);

            return result.transformedCount;
        } catch (Exception e) {
            try {
                Object membersList = classDecl.getClass().getDeclaredMethod("getMembers").invoke(classDecl);
                TransformResult result = createTransformedMembersList(membersList, context);

                java.lang.reflect.Method setMembersMethod = classDecl.getClass().getDeclaredMethod("setMembers",
                        Class.forName("com.sun.tools.javac.util.List"));
                setMembersMethod.invoke(classDecl, result.newList);

                return result.transformedCount;
            } catch (Exception ex) {
                logError("Member transformation failed: " + ex.getMessage());
                if (AST_DEBUG) {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                    ex.printStackTrace(pw);
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, sw.toString());
                }
                return 0;
            }
        }
    }

    private TransformResult createTransformedMembersList(Object originalList, ClassProcessingContext context) throws Exception {
        List<Object> newMembers = new ArrayList<>();
        int transformedCount = 0;

        if (originalList instanceof Iterable) {
            for (Object member : (Iterable<?>) originalList) {
                if (isMethodDeclaration(member)) {
                    Object transformedMember = processMethodMember(member, context);
                    if (transformedMember != member) {
                        transformedCount++;
                    }
                    newMembers.add(transformedMember);
                } else {
                    newMembers.add(member);
                }
            }
        }

        return new TransformResult(convertToJavacList(newMembers), transformedCount);
    }

    private Object processMethodMember(Object member, ClassProcessingContext context) throws Exception {
        String methodName = "unknown";
        try {
            methodName = getMethodName(member);
            debug("Processing method member: " + methodName);

            ExecutableElement methodElement = findMethodElement(context.getClassElement(), methodName);
            debug("Finding methodElement: " + (methodElement != null ? "found" : "not found") + " (" + methodName + ")");

            boolean isEmpty = isEmptyMethod(member);
            debug("Empty method check: " + isEmpty + " (" + methodName + ")");

            boolean shouldForceTransform = false;
            try {
                NativeQuery nq = methodElement != null ? methodElement.getAnnotation(NativeQuery.class) : null;
                boolean isGeneratedName = methodName.startsWith("findBy") || methodName.startsWith("findAll")
                        || methodName.startsWith("countBy") || methodName.startsWith("deleteBy")
                        || methodName.startsWith("existsBy") || methodName.startsWith("save")
                        || methodName.startsWith("update");
                boolean isOptionalReturn = methodElement != null && methodElement.getReturnType().toString().startsWith("java.util.Optional");
                shouldForceTransform = !isOptionalReturn && ((nq != null) || isGeneratedName);
                debug("Force transform check: " + shouldForceTransform + " (" + methodName + ")");
            } catch (Exception ignore) { }

            if (methodElement != null && (isEmpty || shouldForceTransform)) {
                debug("Starting method transformation: " + methodName);

                Object result = createImplementedMethod(member, methodName, methodElement, context);

                boolean isTransformed = checkMethodBodyChanged(member, methodName);
                debug("Body changed check: " + isTransformed);

                return result;
            }

            return member;
        } catch (Exception e) {
            logError("Method member processing failed: " + methodName + " - " + e.getMessage());
            if (AST_DEBUG) {
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                e.printStackTrace(pw);
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, sw.toString());
            }
            return member;
        }
    }

    private boolean checkMethodBodyChanged(Object methodDecl, String methodName) {
        try {
            Object body = methodDecl.getClass().getDeclaredMethod("getBody").invoke(methodDecl);
            if (body == null) {
                return false;
            }
            Object statements = body.getClass().getDeclaredMethod("getStatements").invoke(body);
            if (statements instanceof List) {
                List<?> stmtList = (List<?>) statements;
                if (stmtList.size() > 1) {
                    return true;
                }
                if (stmtList.size() == 1) {
                    Object stmt = stmtList.get(0);

                    return !isReturnNullStatement(stmt);
                }
            }
            return false;
        } catch (Exception e) {
            if (AST_DEBUG) debug("Body change check failed: " + methodName + " - " + e.getMessage());
            return false;
        }
    }

    private Object convertToJavacList(List<Object> members) throws Exception {
        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        java.lang.reflect.Method fromMethod = javacListClass.getDeclaredMethod("from", Object[].class);
        return fromMethod.invoke(null, new Object[]{members.toArray()});
    }

    private Object createImplementedMethod(Object originalMethod, String methodName,
                                          ExecutableElement methodElement, ClassProcessingContext context) throws Exception {
        try {
            NativeQuery nativeQuery = methodElement.getAnnotation(NativeQuery.class);
            if (nativeQuery != null) {
    
                return processNativeQueryMethod(nativeQuery, methodElement, originalMethod);
            }
            if (context.isNativeQueryOnly()) {
    
                return originalMethod;
            }

            Object result = processGeneratedSqlMethod(methodName, methodElement, originalMethod, context);
            if (result == null) {
                logError("Method implementation result is null: " + methodName);
                return originalMethod;
            }
            return result;
        } catch (Exception e) {
            logError("Method implementation processing failed: " + methodName + " - " + e.getMessage());
            if (AST_DEBUG) {
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                e.printStackTrace(pw);
                processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, sw.toString());
            }
            return originalMethod;
        }
    }

    private Object processNativeQueryMethod(NativeQuery nativeQuery, ExecutableElement methodElement,
                                           Object originalMethod) throws Exception {
        String sql = nativeQuery.value();
        List<ParameterInfo> methodParams = parameterProcessor.analyzeMethodParameters(methodElement);

        Object queryExecution = createQueryExecution(sql, nativeQuery, methodElement, methodParams);
        return replaceMethodBody(originalMethod, queryExecution);
    }

    private Object createQueryExecution(String sql, NativeQuery nativeQuery, ExecutableElement methodElement,
                                       List<ParameterInfo> methodParams) throws Exception {
        boolean isUpdate = isUpdateQuery(sql);

        if (hasNamedParameters(sql)) {
            return createNamedParameterExecution(sql, nativeQuery, methodElement, methodParams, isUpdate);
        } else {
            return createPositionalParameterExecution(sql, nativeQuery, methodElement, methodParams, isUpdate);
        }
    }

    private Object createNamedParameterExecution(String sql, NativeQuery nativeQuery, ExecutableElement methodElement,
                                                List<ParameterInfo> methodParams, boolean isUpdate) throws Exception {
        List<String> namedParams = parameterProcessor.extractNamedParameters(sql);
        try {
            parameterProcessor.validateParameterMapping(namedParams, methodParams, methodElement.getSimpleName().toString());
        } catch (RuntimeException ex) {
            // Parameter validation failed - continue with generation
        }

        String resultTypeName = getResultTypeName(nativeQuery, methodElement);
        String columnMappingStr = String.join(",", nativeQuery.columnMapping());
        boolean isVoid = "void".equals(methodElement.getReturnType().toString());
        return queryExecutor.createNamedParameterQueryExecution(sql, methodElement, isUpdate,
                nativeQuery.mappingType(), resultTypeName,
                columnMappingStr, methodParams, isVoid);
    }

    private Object createPositionalParameterExecution(String sql, NativeQuery nativeQuery, ExecutableElement methodElement,
                                                     List<ParameterInfo> methodParams, boolean isUpdate) throws Exception {
        int placeholderCount = parameterProcessor.countPositionalParameters(sql);
        try {
            parameterProcessor.validatePositionalParameters(placeholderCount, methodParams,
                methodElement.getSimpleName().toString());
        } catch (RuntimeException ex) {
            // Parameter validation failed - continue with generation
        }

        String resultTypeName = getResultTypeName(nativeQuery, methodElement);
        String columnMappingStr = String.join(",", nativeQuery.columnMapping());
        boolean isVoid = "void".equals(methodElement.getReturnType().toString());
        return queryExecutor.createPositionalParameterQueryExecution(sql, methodElement, isUpdate,
                nativeQuery.mappingType(), resultTypeName,
                columnMappingStr, methodParams, isVoid);
    }

    private String getResultTypeName(NativeQuery nativeQuery, ExecutableElement methodElement) {
        try {
            Class<?> resultType = nativeQuery.resultType();
            if (resultType != Object.class) {
                return resultType.getSimpleName();
            }

            if (methodElement != null) {
                return methodElement.getReturnType().toString();
            }

            return "Object";

        } catch (MirroredTypeException mte) {
            TypeMirror typeMirror = mte.getTypeMirror();
            String fullName = typeMirror.toString();

            if ("java.lang.Object".equals(fullName) && methodElement != null) {
                return methodElement.getReturnType().toString();
            }

            return fullName;
        }
    }

    private Object processGeneratedSqlMethod(String methodName, ExecutableElement methodElement, 
                                            Object originalMethod, ClassProcessingContext context) throws Exception {
        
        Object methodBody = generateMethodBody(methodName, methodElement, context);
        
        if (methodBody == null) {
            return originalMethod;
        }
        
        if (!isReturnStatement(methodBody) && !isVoidReturnType(methodElement)) {
            Object defaultReturn = createDefaultReturnFor(methodElement);
            if (defaultReturn != null) {
                methodBody = defaultReturn;
            }
        }
        
        Object result = replaceMethodBody(originalMethod, methodBody);
        
        return result;
    }

    private Object generateMethodBody(String methodName, ExecutableElement methodElement, 
                                     ClassProcessingContext context) throws Exception {
        String entityFqn = context.getEntityType().toString();
        
        if (methodName.startsWith("findBy") || methodName.startsWith("findAll")) {
            return generateFindMethod(methodName, methodElement, context, entityFqn);
        } else if (methodName.startsWith("countBy")) {
            return sqlGenerator.createCountByImplementationWithValidation(methodName, context.getTableName(), context.getEntityInfo(), methodElement, astHelper);
        } else if (methodName.startsWith("deleteBy")) {
            return sqlGenerator.createDeleteByImplementationWithValidation(methodName, context.getTableName(), context.getEntityInfo(), methodElement, astHelper);
        } else if (methodName.startsWith("existsBy")) {
            return sqlGenerator.createExistsByImplementationWithValidation(methodName, context.getTableName(), context.getEntityInfo(), methodElement, astHelper);
        } else if (methodName.startsWith("save")) {
            return sqlGenerator.createSaveImplementationWithEntity(context.getEntityName(), context.getTableName(), context.getEntityInfo(), methodElement, astHelper);
        } else if (methodName.startsWith("update")) {
            return sqlGenerator.createUpdateImplementationWithEntity(context.getEntityName(), context.getTableName(), context.getEntityInfo(), methodElement, astHelper);
        } else {
            return createDebugStatement(methodName);
        }
    }

    private Object generateFindMethod(String methodName, ExecutableElement methodElement, 
                                     ClassProcessingContext context, String entityFqn) throws Exception {
        if (methodName.equals("findAll")) {
            return sqlGenerator.createFindAllImplementation(entityFqn, context.getTableName(), astHelper);
        } else {
            return sqlGenerator.createFindByImplementationWithValidation(methodName, context.getEntityName(), entityFqn, 
                    context.getTableName(), context.getEntityInfo(), methodElement, astHelper);
        }
    }

    // Utility methods
    private TypeMirror getEntityType(SqlGenerator annotation) {
        try {
            annotation.entity();
            return null; // This line is never executed
        } catch (MirroredTypeException mte) {
            return mte.getTypeMirror();
        }
    }

    private boolean isVoidEntity(SqlGenerator annotation) {
        try {
            Class<?> entityClass = annotation.entity();
            return entityClass == void.class;
        } catch (MirroredTypeException mte) {
            String tm = mte.getTypeMirror().toString();
            return "void".equals(tm) || "java.lang.Void".equals(tm);
        } catch (Exception e) {
            return false;
        }
    }

    private String getSimpleName(TypeMirror typeMirror) {
        String fullName = typeMirror.toString();
        return fullName.substring(fullName.lastIndexOf('.') + 1);
    }

    private String camelToSnake(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    private String camelToKebab(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase();
    }

    private String pascalToCamel(String pascal) {
        if (pascal == null || pascal.isEmpty()) return pascal;
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    private String applyNamingStrategy(String baseName, NamingStrategy strategy) {

        switch (strategy) {
            case CAMEL_CASE:
                return pascalToCamel(baseName);
            case PASCAL_CASE:
                return baseName;
            case KEBAB_CASE:
                return camelToKebab(pascalToCamel(baseName));
            case SNAKE_CASE:
            default:
                return camelToSnake(pascalToCamel(baseName));
        }
    }

    private EntityInfo analyzeEntity(TypeMirror entityType) {
        EntityInfo entityInfo = new EntityInfo();
        try {
            Element element = processingEnv.getTypeUtils().asElement(entityType);
            if (element instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) element;
                List<String> fields = new ArrayList<>();
                
                for (Element enclosedElement : typeElement.getEnclosedElements()) {
                    if (enclosedElement.getKind() == ElementKind.FIELD) {
                        fields.add(enclosedElement.getSimpleName().toString());
                    }
                }
                
                entityInfo.setFields(fields);
            }
        } catch (Exception e) {
            logError("Entity analysis failed: " + e.getMessage());
        }
        return entityInfo;
    }

    private ExecutableElement findMethodElement(TypeElement classElement, String methodName) {
        for (Element enclosedElement : classElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD &&
                enclosedElement.getSimpleName().toString().equals(methodName)) {
                return (ExecutableElement) enclosedElement;
            }
        }
        return null;
    }

    private boolean isMethodDeclaration(Object member) throws Exception {
        return member.getClass().getSimpleName().equals("JCMethodDecl");
    }

    private boolean isEmptyMethod(Object methodDecl) {
        try {
            Object body = methodDecl.getClass().getDeclaredMethod("getBody").invoke(methodDecl);
            if (body == null) {
                return true;
            }
            Object statements = body.getClass().getDeclaredMethod("getStatements").invoke(body);
            if (statements instanceof List) {
                List<?> stmtList = (List<?>) statements;
                if (stmtList.isEmpty()) {
                    return true;
                }

                if (stmtList.size() == 1) {
                    Object stmt = stmtList.get(0);
                    if (isReturnNullStatement(stmt)) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        } catch (Exception e) {
            if (AST_DEBUG) debug("Method check failed: " + e.getMessage());
            return false;
        }
    }
    
    private boolean isReturnNullStatement(Object stmt) {
        try {
            String stmtClass = stmt.getClass().getSimpleName();
            if ("JCReturn".equals(stmtClass)) {
                Object expr = stmt.getClass().getDeclaredMethod("getExpression").invoke(stmt);
                if (expr == null) {
                    return true;
                }
                String exprClass = expr.getClass().getSimpleName();
                if ("JCLiteral".equals(exprClass)) {
                    Object value = expr.getClass().getDeclaredMethod("getValue").invoke(expr);
                    return value == null;
                }
            }
            return false;
        } catch (Exception e) {
            if (AST_DEBUG) debug("Return null check failed: " + e.getMessage());
            return false;
        }
    }

    private String getMethodName(Object methodDecl) throws Exception {
        Object name = methodDecl.getClass().getDeclaredMethod("getName").invoke(methodDecl);
        return name.toString();
    }

    private Object replaceMethodBody(Object originalMethod, Object newStatement) throws Exception {
        try {
            Object newBlock = astHelper.createBlockFromStatement(newStatement);
            // Modify the body field of the original method
            java.lang.reflect.Field bodyField = originalMethod.getClass().getDeclaredField("body");
            bodyField.setAccessible(true);
            bodyField.set(originalMethod, newBlock);
            return originalMethod;
        } catch (Exception e) {
            try {
                java.lang.reflect.Field bodyField = originalMethod.getClass().getDeclaredField("body");
                bodyField.setAccessible(true);
                bodyField.set(originalMethod, newStatement);
                return originalMethod;
            } catch (Exception ex) {
                logError("Method body replacement failed: " + ex.getMessage());
                return originalMethod;
            }
        }
    }

    private Object createDebugStatement(String methodName) throws Exception {
        Object systemOut = astHelper.createQualifiedIdent("System.out");
        Object printlnAccess = astHelper.createFieldAccess(systemOut, "println");
        Object messageLiteral = astHelper.createLiteral("Method " + methodName + " called (implementation needed)");
        Object printCall = astHelper.createMethodCall(printlnAccess, messageLiteral);
        return astHelper.createExpressionStatement(printCall);
    }

    // Safety utility methods
    private boolean isReturnStatement(Object stmt) {
        return stmt != null && "JCReturn".equals(stmt.getClass().getSimpleName());
    }

    private boolean isVoidReturnType(ExecutableElement methodElement) {
        try {
            return "void".equals(methodElement.getReturnType().toString());
        } catch (Exception e) {
            return false;
        }
    }

    private Object createDefaultReturnFor(ExecutableElement methodElement) {
        try {
            String rt = methodElement.getReturnType().toString();
            if ("void".equals(rt)) {
                return null;
            }
            if (rt.equals("boolean") || rt.equals("java.lang.Boolean")) {
                Object lit = astHelper.createLiteral(false);
                return astHelper.createReturnStatement(lit);
            }
            if (rt.equals("int") || rt.equals("java.lang.Integer")) {
                Object lit = astHelper.createLiteral(0);
                return astHelper.createReturnStatement(lit);
            }
            if (rt.equals("long") || rt.equals("java.lang.Long")) {
                Object lit = astHelper.createLiteral(0L);
                return astHelper.createReturnStatement(lit);
            }
            if (rt.equals("double") || rt.equals("java.lang.Double")) {
                Object lit = astHelper.createLiteral(0.0);
                return astHelper.createReturnStatement(lit);
            }
            if (rt.startsWith("java.util.List")) {
                Object collections = astHelper.createQualifiedIdent("java.util.Collections");
                Object emptyList = astHelper.createFieldAccess(collections, "emptyList");
                Object call = astHelper.createMethodCall(emptyList);
                return astHelper.createReturnStatement(call);
            }
            if (rt.startsWith("java.util.Optional")) {
                Object optional = astHelper.createQualifiedIdent("java.util.Optional");
                Object empty = astHelper.createFieldAccess(optional, "empty");
                Object call = astHelper.createMethodCall(empty);
                return astHelper.createReturnStatement(call);
            }

            try {
                java.lang.reflect.Method m = astHelper.getClass().getMethod("createLiteral", Object.class);
                Object nullLit = m.invoke(astHelper, new Object[]{null});
                return astHelper.createReturnStatement(nullLit);
            } catch (Exception ex) {

                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isUpdateQuery(String sql) {
        String upperSql = sql.trim().toUpperCase();
        return upperSql.startsWith("INSERT") || upperSql.startsWith("UPDATE") || upperSql.startsWith("DELETE");
    }

    private boolean hasNamedParameters(String sql) {
        return sql.contains(":");
    }

    // Logging methods
    private void logInfo(String message) {
        if (AST_VERBOSE) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
        }
    }

    private void logError(String message) {

        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message);
    }

    private void debug(String message) {
        if (AST_DEBUG) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
        }
    }

    // Inner classes
    public static class EntityInfo {
        private List<String> fields = new ArrayList<>();

        public List<String> getFields() { return fields; }
        public void setFields(List<String> fields) { this.fields = fields; }
    }

    private static class ClassProcessingContext {
        private final TypeElement classElement;
        private final TypeMirror entityType;
        private final String entityName;
        private final String tableName;
        private final EntityInfo entityInfo;
        private final NamingStrategy namingStrategy;
        private final boolean nativeQueryOnly;

        public ClassProcessingContext(TypeElement classElement, TypeMirror entityType,
                                      String entityName, String tableName, EntityInfo entityInfo,
                                      NamingStrategy namingStrategy,
                                      boolean nativeQueryOnly) {
            this.classElement = classElement;
            this.entityType = entityType;
            this.entityName = entityName;
            this.tableName = tableName;
            this.entityInfo = entityInfo;
            this.namingStrategy = namingStrategy;
            this.nativeQueryOnly = nativeQueryOnly;
        }

        public TypeElement getClassElement() { return classElement; }
        public TypeMirror getEntityType() { return entityType; }
        public String getEntityName() { return entityName; }
        public String getTableName() { return tableName; }
        public EntityInfo getEntityInfo() { return entityInfo; }
        public String getClassName() { return classElement.getSimpleName().toString(); }
        public NamingStrategy getNamingStrategy() { return namingStrategy; }
        public boolean isNativeQueryOnly() { return nativeQueryOnly; }
    }
    
    private static class TransformResult {
        final Object newList;
        final int transformedCount;
        
        TransformResult(Object newList, int transformedCount) {
            this.newList = newList;
            this.transformedCount = transformedCount;
        }
    }
}
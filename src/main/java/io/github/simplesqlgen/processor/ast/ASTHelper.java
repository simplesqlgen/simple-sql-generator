package io.github.simplesqlgen.processor.ast;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * AST manipulation helper class
 * Handles code generation using JavaC internal APIs via reflection
 */
public class ASTHelper {

    
    private Object trees;
    private Object treeMaker;
    private Object names;
    private Object context;
    private boolean astAvailable = false;

    private Class<?> javacEnvClass;
    private Class<?> jcTreeClass;
    private Class<?> jcMethodDeclClass;
    private Class<?> jcClassDeclClass;
    private Class<?> jcBlockClass;
    private Class<?> jcReturnClass;
    private Class<?> jcLiteralClass;
    private Class<?> jcStatementClass;

    /**
     * Initialize AST tools
     */
    public void initialize(ProcessingEnvironment processingEnv) throws Exception {
        loadAllJavacClasses();
        
        if (!isJavacProcessingEnvironment(processingEnv)) {
            return;
        }

        this.context = extractContext(processingEnv);
        if (this.context == null) {
            return;
        }

        initializeASTTools(processingEnv);
        this.astAvailable = true;
    }

    /**
     * Check if AST is available
     */
    public boolean isASTAvailable() {
        return astAvailable;
    }

    private void loadAllJavacClasses() throws Exception {
        this.javacEnvClass = Class.forName("com.sun.tools.javac.processing.JavacProcessingEnvironment");
        this.jcTreeClass = Class.forName("com.sun.tools.javac.tree.JCTree");
        this.jcMethodDeclClass = Class.forName("com.sun.tools.javac.tree.JCTree$JCMethodDecl");
        this.jcClassDeclClass = Class.forName("com.sun.tools.javac.tree.JCTree$JCClassDecl");
        this.jcBlockClass = Class.forName("com.sun.tools.javac.tree.JCTree$JCBlock");
        this.jcReturnClass = Class.forName("com.sun.tools.javac.tree.JCTree$JCReturn");
        this.jcLiteralClass = Class.forName("com.sun.tools.javac.tree.JCTree$JCLiteral");
        this.jcStatementClass = Class.forName("com.sun.tools.javac.tree.JCTree$JCStatement");
    }

    private boolean isJavacProcessingEnvironment(ProcessingEnvironment processingEnv) {
        return javacEnvClass.isInstance(processingEnv);
    }

    private Object extractContext(ProcessingEnvironment processingEnv) throws Exception {
        Field contextField = javacEnvClass.getDeclaredField("context");
        contextField.setAccessible(true);
        return contextField.get(processingEnv);
    }

    private void initializeASTTools(ProcessingEnvironment processingEnv) throws Exception {
        Class<?> treesClass = Class.forName("com.sun.source.util.Trees");
        Method instanceMethod = treesClass.getMethod("instance", ProcessingEnvironment.class);
        this.trees = instanceMethod.invoke(null, processingEnv);

        Class<?> treeMakerClass = Class.forName("com.sun.tools.javac.tree.TreeMaker");
        Method instanceTreeMakerMethod = treeMakerClass.getMethod("instance", Class.forName("com.sun.tools.javac.util.Context"));
        this.treeMaker = instanceTreeMakerMethod.invoke(null, this.context);

        Class<?> namesClass = Class.forName("com.sun.tools.javac.util.Names");
        Method instanceNamesMethod = namesClass.getMethod("instance", Class.forName("com.sun.tools.javac.util.Context"));
        this.names = instanceNamesMethod.invoke(null, this.context);
    }

    /**
     * Get compilation unit
     */
    public Object getCompilationUnit(Object treePath) throws Exception {
        Method getCompilationUnitMethod = treePath.getClass().getDeclaredMethod("getCompilationUnit");
        return getCompilationUnitMethod.invoke(treePath);
    }

    /**
     * Get class declaration
     */
    public Object getClassDecl(Object treePath) throws Exception {
        Object compilationUnit = getCompilationUnit(treePath);
        Method getTypeDeclsMethod = compilationUnit.getClass().getDeclaredMethod("getTypeDecls");
        List<?> typeDecls = (List<?>) getTypeDeclsMethod.invoke(compilationUnit);
        return typeDecls.get(0);
    }

    /**
     * Add required imports
     */
    public void addRequiredImports(Object compilationUnit) throws Exception {
        String[] requiredImports = {
                "org.springframework.beans.factory.annotation.Autowired",
                "org.springframework.jdbc.core.JdbcTemplate",
                "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
                "org.springframework.jdbc.core.namedparam.MapSqlParameterSource",
                "org.springframework.jdbc.core.BeanPropertyRowMapper",
                "org.springframework.jdbc.core.ColumnMapRowMapper",
                "org.springframework.jdbc.core.RowMapper",
                "java.util.List",
                "java.util.ArrayList",
                "java.util.Collection"
        };

        try {
            Field defsField = compilationUnit.getClass().getDeclaredField("defs");
            defsField.setAccessible(true);
            Object existingDefs = defsField.get(compilationUnit);

            for (String importPath : requiredImports) {
                if (!hasImport(existingDefs, importPath)) {
                    addSingleImportIfMissing(compilationUnit, importPath);
                }
            }
        } catch (Exception e) {
        }
    }

    private void addSingleImportIfMissing(Object compilationUnit, String importPath) throws Exception {
        try {
            Method getDefsMethod = compilationUnit.getClass().getDeclaredMethod("defs");
            Object existingDefs = getDefsMethod.invoke(compilationUnit);

            if (!hasImport(existingDefs, importPath)) {
                Object importDecl = createImportDecl(importPath);
                Method prependMethod = existingDefs.getClass().getDeclaredMethod("prepend", Object.class);
                Object newDefs = prependMethod.invoke(existingDefs, importDecl);
                

                Field defsField = compilationUnit.getClass().getDeclaredField("defs");
                defsField.setAccessible(true);
                defsField.set(compilationUnit, newDefs);
            }
        } catch (Exception e) {

        }
    }

    /**
     * Create import declaration
     */
    public Object createImportDecl(String qualifiedName) throws Exception {
        try {
            Object qualifiedIdent = createQualifiedIdent(qualifiedName);
            Method importMethod = treeMaker.getClass().getDeclaredMethod("Import", 
                    jcTreeClass, boolean.class);
            return importMethod.invoke(treeMaker, qualifiedIdent, false);
        } catch (Exception e) {
            try {
                Object qualifiedIdent = createQualifiedIdent(qualifiedName);
                Method importMethod = treeMaker.getClass().getDeclaredMethod("Import", 
                        Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"), boolean.class);
                return importMethod.invoke(treeMaker, qualifiedIdent, false);
            } catch (Exception ex) {
                throw new RuntimeException("Import creation failed: " + qualifiedName, ex);
            }
        }
    }

    /**
     * Create qualified identifier
     */
    public Object createQualifiedIdent(String qualifiedName) throws Exception {
        Method identMethod = treeMaker.getClass().getDeclaredMethod("Ident", 
                Class.forName("com.sun.tools.javac.util.Name"));
        Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
        
        String[] parts = qualifiedName.split("\\.");
        Object result = identMethod.invoke(treeMaker, fromStringMethod.invoke(names, parts[0]));
        
        for (int i = 1; i < parts.length; i++) {
            Method selectMethod = treeMaker.getClass().getDeclaredMethod("Select", 
                    Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"), 
                    Class.forName("com.sun.tools.javac.util.Name"));
            result = selectMethod.invoke(treeMaker, result, fromStringMethod.invoke(names, parts[i]));
        }
        
        return result;
    }

    private boolean isPackageDecl(Object def) throws Exception {
        return def.getClass().getSimpleName().equals("JCPackageDecl");
    }

    private boolean isImportDecl(Object def) throws Exception {
        return def.getClass().getSimpleName().equals("JCImport");
    }

    /**
     * Transform methods
     */
    public int transformMethods(Object classDecl, TypeElement classElement, TypeMirror entityType,
                               String tableName, Object entityInfo) throws Exception {
        Method getMembersMethod = classDecl.getClass().getDeclaredMethod("getMembers");
        Object membersList = getMembersMethod.invoke(classDecl);

        Object newMembersList = createTransformedMembersList(membersList, classElement, entityType, tableName, entityInfo);


        try {
            Method setMembersMethod = classDecl.getClass().getDeclaredMethod("setMembers", 
                    Class.forName("com.sun.tools.javac.util.List"));
            setMembersMethod.invoke(classDecl, newMembersList);
        } catch (NoSuchMethodException e) {

            try {
                Field defsField = classDecl.getClass().getDeclaredField("defs");
                defsField.setAccessible(true);
                defsField.set(classDecl, newMembersList);
            } catch (Exception ex) {

            }
        }

        return 0;
    }

    private boolean hasImport(Object existingDefs, String importPath) throws Exception {
        try {
            if (existingDefs instanceof Iterable) {
                for (Object def : (Iterable<?>) existingDefs) {
                    if (isImportDecl(def)) {
                        try {
                            Field qualidField = def.getClass().getDeclaredField("qualid");
                            qualidField.setAccessible(true);
                            Object qualId = qualidField.get(def);
                            String existingImport = qualId.toString();
                            if (existingImport.equals(importPath)) {
                                return true;
                            }
                        } catch (Exception e) {

                        }
                    }
                }
            }
        } catch (Exception e) {

        }
        return false;
    }

    /**
     * Inject JdbcTemplate constructor (improved version)
     */
    public void injectJdbcTemplateConstructor(Object classDecl) throws Exception {
        try {
            Method getMembersMethod = classDecl.getClass().getDeclaredMethod("getMembers");
            Object membersList = getMembersMethod.invoke(classDecl);

            if (!hasJdbcTemplateField(membersList) && !hasConstructorWithJdbcTemplate(membersList)) {
                Method prependMethod = membersList.getClass().getDeclaredMethod("prepend", Object.class);
                

                Object jdbcTemplateField = createFinalField("JdbcTemplate", "jdbcTemplate");
                membersList = prependMethod.invoke(membersList, jdbcTemplateField);

                Object namedJdbcTemplateField = createFinalField("NamedParameterJdbcTemplate", "namedParameterJdbcTemplate");
                membersList = prependMethod.invoke(membersList, namedJdbcTemplateField);


                try {
                    Object constructor = createSafeJdbcTemplateConstructor();
                    if (constructor != null) {
                        membersList = prependMethod.invoke(membersList, constructor);
                    }
                } catch (Exception constructorEx) {
                }

                updateClassMembers(classDecl, membersList);
            }
        } catch (Exception e) {
        }
    }

    /**
     * Inject @Autowired fields only (without constructor)
     */
    public void injectAutowiredFields(Object classDecl) throws Exception {
        try {
            Method getMembersMethod = classDecl.getClass().getDeclaredMethod("getMembers");
            Object membersList = getMembersMethod.invoke(classDecl);

            if (!hasJdbcTemplateField(membersList)) {
                Method prependMethod = membersList.getClass().getDeclaredMethod("prepend", Object.class);
                
                Object jdbcTemplateField = createAutowiredField("JdbcTemplate", "jdbcTemplate");
                membersList = prependMethod.invoke(membersList, jdbcTemplateField);

                Object namedJdbcTemplateField = createAutowiredField("NamedParameterJdbcTemplate", "namedParameterJdbcTemplate");
                membersList = prependMethod.invoke(membersList, namedJdbcTemplateField);

                updateClassMembers(classDecl, membersList);
            }
        } catch (Exception e) {
        }
    }

    private Object createSimpleAutowiredField(String fieldType, String fieldName) throws Exception {
        long privateFlag = 1L << 1;
        Object modifiers = createModifiers(privateFlag, null);

        Object type;
        if (fieldType.equals("JdbcTemplate")) {
            type = createQualifiedIdent("org.springframework.jdbc.core.JdbcTemplate");
        } else {
            type = createQualifiedIdent("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate");
        }

        Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
        Object fieldNameObj = fromStringMethod.invoke(names, fieldName);

        Method varDefMethod = treeMaker.getClass().getDeclaredMethod("VarDef", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCModifiers"),
                Class.forName("com.sun.tools.javac.util.Name"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));

        return varDefMethod.invoke(treeMaker, modifiers, fieldNameObj, type, null);
    }

    private Object createAutowiredField(String fieldType, String fieldName) throws Exception {
        Object autowiredType = createQualifiedIdent("org.springframework.beans.factory.annotation.Autowired");
        Object autowiredAnnotation = createAnnotation(autowiredType);

        long privateFlag = 1L << 1;

        Object annotations;
        Object modifiers;
        
        if (autowiredAnnotation != null) {
            annotations = createSingletonList(autowiredAnnotation);
            modifiers = createModifiers(privateFlag, annotations);
        } else {
            modifiers = createModifiers(privateFlag, null);
        }

        Object type;
        if (fieldType.equals("JdbcTemplate")) {
            type = createQualifiedIdent("org.springframework.jdbc.core.JdbcTemplate");
        } else {
            type = createQualifiedIdent("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate");
        }

        Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
        Object fieldNameObj = fromStringMethod.invoke(names, fieldName);

        Method varDefMethod = treeMaker.getClass().getDeclaredMethod("VarDef", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCModifiers"),
                Class.forName("com.sun.tools.javac.util.Name"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));

        return varDefMethod.invoke(treeMaker, modifiers, fieldNameObj, type, null);
    }

    public Object createAnnotation(Object annotationType) throws Exception {
        try {
            Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
            Method nilMethod = javacListClass.getDeclaredMethod("nil");
            Object emptyArguments = nilMethod.invoke(null);
            

            try {
                Method annotationMethod = treeMaker.getClass().getDeclaredMethod("Annotation", 
                        Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"), javacListClass);
                return annotationMethod.invoke(treeMaker, annotationType, emptyArguments);
            } catch (Exception e1) {
                try {
                    Method annotationMethod = treeMaker.getClass().getDeclaredMethod("Annotation", 
                            Class.forName("com.sun.tools.javac.tree.JCTree"), javacListClass);
                    return annotationMethod.invoke(treeMaker, annotationType, emptyArguments);
                } catch (Exception e2) {
                    try {
                        Method annotationMethod = treeMaker.getClass().getDeclaredMethod("Annotation", 
                                Object.class, Object.class);
                        return annotationMethod.invoke(treeMaker, annotationType, emptyArguments);
                    } catch (Exception e3) {
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    public Object createModifiers(long flags, Object annotations) throws Exception {
        Method modifiersMethod = treeMaker.getClass().getDeclaredMethod("Modifiers", 
                long.class, Class.forName("com.sun.tools.javac.util.List"));

        if (annotations == null) {
            Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
            Method nilMethod = javacListClass.getDeclaredMethod("nil");
            annotations = nilMethod.invoke(null);
        }

        return modifiersMethod.invoke(treeMaker, flags, annotations);
    }

    public Object createSingletonList(Object element) throws Exception {
        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        Method ofMethod = javacListClass.getDeclaredMethod("of", Object.class);
        return ofMethod.invoke(null, element);
    }

    private boolean hasJdbcTemplateField(Object membersList) throws Exception {
        if (membersList instanceof Iterable) {
            for (Object member : (Iterable<?>) membersList) {
                if (member.getClass().getSimpleName().equals("JCVariableDecl")) {
                    Method getNameMethod = member.getClass().getDeclaredMethod("getName");
                    Object name = getNameMethod.invoke(member);
                    String nameStr = name.toString();
                    if ("jdbcTemplate".equals(nameStr) || "namedParameterJdbcTemplate".equals(nameStr)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasConstructorWithJdbcTemplate(Object membersList) throws Exception {
        if (membersList instanceof Iterable) {
            for (Object member : (Iterable<?>) membersList) {
                if (member.getClass().getSimpleName().equals("JCMethodDecl")) {
                    Method getNameMethod = member.getClass().getDeclaredMethod("getName");
                    Object name = getNameMethod.invoke(member);
                    String nameStr = name.toString();
                    if ("<init>".equals(nameStr)) {

                        Method getParametersMethod = member.getClass().getDeclaredMethod("getParameters");
                        Object params = getParametersMethod.invoke(member);
                        if (params instanceof Iterable) {
                            for (Object param : (Iterable<?>) params) {
                                Method getTypeMethod = param.getClass().getDeclaredMethod("getType");
                                Object type = getTypeMethod.invoke(param);
                                String typeStr = type.toString();
                                if (typeStr.contains("JdbcTemplate")) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private Object createFinalField(String fieldType, String fieldName) throws Exception {
        long privateFlag = 1L << 1;
        long finalFlag = 1L << 4;
        long flags = privateFlag | finalFlag;
        
        Object modifiers = createModifiers(flags, null);

        Object type;
        if (fieldType.equals("JdbcTemplate")) {
            type = createQualifiedIdent("org.springframework.jdbc.core.JdbcTemplate");
        } else {
            type = createQualifiedIdent("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate");
        }

        Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
        Object fieldNameObj = fromStringMethod.invoke(names, fieldName);

        Method varDefMethod = treeMaker.getClass().getDeclaredMethod("VarDef", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCModifiers"),
                Class.forName("com.sun.tools.javac.util.Name"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));

        return varDefMethod.invoke(treeMaker, modifiers, fieldNameObj, type, null);
    }

    private Object createJdbcTemplateConstructor() throws Exception {
        Object jdbcTemplateParam = createConstructorParameter("JdbcTemplate", "jdbcTemplate");
        Object namedJdbcTemplateParam = createConstructorParameter("NamedParameterJdbcTemplate", "namedParameterJdbcTemplate");
        
        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        Method ofMethod = javacListClass.getDeclaredMethod("of", Object.class, Object.class);
        Object paramsList = ofMethod.invoke(null, jdbcTemplateParam, namedJdbcTemplateParam);

        Object jdbcTemplateAssignment = createFieldAssignment("jdbcTemplate", "jdbcTemplate");
        Object namedJdbcTemplateAssignment = createFieldAssignment("namedParameterJdbcTemplate", "namedParameterJdbcTemplate");
        
        Object assignmentsList = ofMethod.invoke(null, jdbcTemplateAssignment, namedJdbcTemplateAssignment);
        Object constructorBody = createConstructorBody(assignmentsList);

        long publicFlag = 1L << 0;
        Object modifiers = createModifiers(publicFlag, null);
        
        Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
        Object constructorName = fromStringMethod.invoke(names, "<init>");
        
        Method nilMethod = javacListClass.getDeclaredMethod("nil");
        Object emptyTypeParams = nilMethod.invoke(null);
        Object emptyThrows = nilMethod.invoke(null);

        Method methodDefMethod = treeMaker.getClass().getDeclaredMethod("MethodDef", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCModifiers"),
                Class.forName("com.sun.tools.javac.util.Name"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCBlock"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));

        return methodDefMethod.invoke(treeMaker, modifiers, constructorName, null, 
                emptyTypeParams, paramsList, emptyThrows, constructorBody, null);
    }

    private Object createConstructorParameter(String paramType, String paramName) throws Exception {
        Object type = paramType.equals("JdbcTemplate") 
            ? createQualifiedIdent("org.springframework.jdbc.core.JdbcTemplate")
            : createQualifiedIdent("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate");

        Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
        Object paramNameObj = fromStringMethod.invoke(names, paramName);
        
        long parameterFlag = getParameterFlag();
        Object modifiers = createModifiers(parameterFlag, null);

        Method varDefMethod = treeMaker.getClass().getDeclaredMethod("VarDef", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCModifiers"),
                Class.forName("com.sun.tools.javac.util.Name"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));

        return varDefMethod.invoke(treeMaker, modifiers, paramNameObj, type, null);
    }

    private long getParameterFlag() {
        try {
            Class<?> flagsClass = Class.forName("com.sun.tools.javac.code.Flags");
            Field parameterField = flagsClass.getDeclaredField("PARAMETER");
            return parameterField.getLong(null);
        } catch (Exception e) {
            return 1L << 8;
        }
    }

    private Object createSafeJdbcTemplateConstructor() {
        try {
            return createJdbcTemplateConstructor();
        } catch (Exception e) {
            return null;
        }
    }

    private void updateClassMembers(Object classDecl, Object membersList) {
        try {
            Method setMembersMethod = classDecl.getClass().getDeclaredMethod("setMembers", 
                    Class.forName("com.sun.tools.javac.util.List"));
            setMembersMethod.invoke(classDecl, membersList);
        } catch (NoSuchMethodException e) {
            try {
                Field defsField = classDecl.getClass().getDeclaredField("defs");
                defsField.setAccessible(true);
                defsField.set(classDecl, membersList);
            } catch (Exception ex) {

            }
        } catch (Exception e) {

        }
    }

    private Object createFieldAssignment(String fieldName, String paramName) throws Exception {
        Object thisAccess = createIdent("this");
        Object fieldAccess = createFieldAccess(thisAccess, fieldName);
        Object paramAccess = createIdent(paramName);
        Object assignment = createAssignment(fieldAccess, paramAccess);
        return createExpressionStatement(assignment);
    }

    private Object createConstructorBody(Object statementsList) throws Exception {
        Method blockMethod = treeMaker.getClass().getDeclaredMethod("Block", 
                long.class, Class.forName("com.sun.tools.javac.util.List"));
        return blockMethod.invoke(treeMaker, 0L, statementsList);
    }

    private Object createTransformedMembersList(Object originalList, TypeElement classElement,
                                               TypeMirror entityType, String tableName, Object entityInfo) throws Exception {

        return originalList;
    }

    public Object createMethodCall(Object method, Object... args) throws Exception {
        Method applyMethod = treeMaker.getClass().getDeclaredMethod("Apply", 
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.util.List"));


        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        Method nilMethod = javacListClass.getDeclaredMethod("nil");
        Object emptyTypeArgs = nilMethod.invoke(null);


        Object argsList;
        if (args.length == 0) {
            argsList = nilMethod.invoke(null);
        } else {
            Method fromMethod = javacListClass.getDeclaredMethod("from", Object[].class);
            argsList = fromMethod.invoke(null, new Object[]{args});
        }

        return applyMethod.invoke(treeMaker, emptyTypeArgs, method, argsList);
    }

    public Object createFieldAccess(Object base, String fieldName) throws Exception {
        Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
        Object fieldNameObj = fromStringMethod.invoke(names, fieldName);
        
        Method selectMethod = treeMaker.getClass().getDeclaredMethod("Select", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"), 
                Class.forName("com.sun.tools.javac.util.Name"));
        return selectMethod.invoke(treeMaker, base, fieldNameObj);
    }

    public Object createFieldAccess(String baseName, String fieldName) throws Exception {
        Object baseIdent = createIdent(baseName);
        return createFieldAccess(baseIdent, fieldName);
    }

    public Object createIdent(String name) throws Exception {
        Method identMethod = treeMaker.getClass().getDeclaredMethod("Ident", 
                Class.forName("com.sun.tools.javac.util.Name"));
        Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
        Object nameObj = fromStringMethod.invoke(names, name);
        return identMethod.invoke(treeMaker, nameObj);
    }

    public Object createLiteral(String value) throws Exception {
        Method literalMethod = treeMaker.getClass().getDeclaredMethod("Literal", Object.class);
        return literalMethod.invoke(treeMaker, value);
    }

    public Object createExpressionStatement(Object expr) throws Exception {
        Method execMethod = treeMaker.getClass().getDeclaredMethod("Exec", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));
        return execMethod.invoke(treeMaker, expr);
    }


    public Object getTreePath(javax.lang.model.element.Element element) throws Exception {
        Method getPathMethod = trees.getClass().getDeclaredMethod("getPath", javax.lang.model.element.Element.class);
        return getPathMethod.invoke(trees, element);
    }

    public Object createLiteral(Object value) throws Exception {
        if (!astAvailable) {
            return null;
        }

        if (value == null) {

            try {
                Class<?> typeTagClass = Class.forName("com.sun.tools.javac.code.TypeTag");
                Field botField = typeTagClass.getDeclaredField("BOT");
                botField.setAccessible(true);
                Object botTypeTag = botField.get(null);
                
                Method literalMethod = treeMaker.getClass().getDeclaredMethod("Literal", 
                        typeTagClass, Object.class);
                return literalMethod.invoke(treeMaker, botTypeTag, null);
            } catch (Exception e) {

                try {
                    return createIdent("null");
                } catch (Exception e2) {

                    Method literalMethod = treeMaker.getClass().getDeclaredMethod("Literal", Object.class);
                    return literalMethod.invoke(treeMaker, (Object) null);
                }
            }
        } else if (value instanceof String) {
            return createLiteral((String) value);
        } else {
            Method literalMethod = treeMaker.getClass().getDeclaredMethod("Literal", Object.class);
            return literalMethod.invoke(treeMaker, value);
        }
    }

    public Object createVariable(String name, String type) throws Exception {
        return createIdent(name);
    }

    public Object createVariable(String name) throws Exception {
        return createIdent(name);
    }

    public Object createAssignment(Object left, Object right) throws Exception {
        Method assignMethod = treeMaker.getClass().getDeclaredMethod("Assign", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));
        return assignMethod.invoke(treeMaker, left, right);
    }

    public Object createBlock(List<Object> statements) throws Exception {
        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        Method fromMethod = javacListClass.getDeclaredMethod("from", Object[].class);
        Object statementsList = fromMethod.invoke(null, new Object[]{statements.toArray()});

        Method blockMethod = treeMaker.getClass().getDeclaredMethod("Block", 
                long.class, Class.forName("com.sun.tools.javac.util.List"));
        return blockMethod.invoke(treeMaker, 0L, statementsList);
    }

    public Object createBlockFromStatement(Object statement) throws Exception {
        List<Object> statements = new ArrayList<>();
        statements.add(statement);
        return createBlock(statements);
    }

    public Object createReturnStatement(Object expr) throws Exception {
        Method returnMethod = treeMaker.getClass().getDeclaredMethod("Return", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));
        return returnMethod.invoke(treeMaker, expr);
    }

    public Object createNewStringBuilder(String initialValue) throws Exception {
        Object stringBuilderType = createQualifiedIdent("java.lang.StringBuilder");
        Object stringLiteral = createLiteral(initialValue);
        
        Method newClassMethod = treeMaker.getClass().getDeclaredMethod("NewClass", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCClassDecl"));

        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        Method ofMethod = javacListClass.getDeclaredMethod("of", Object.class);
        Object argsList = ofMethod.invoke(null, stringLiteral);
        
        Method nilMethod = javacListClass.getDeclaredMethod("nil");
        Object emptyList = nilMethod.invoke(null);

        return newClassMethod.invoke(treeMaker, null, emptyList, stringBuilderType, argsList, null);
    }

    public Object createUnaryExpression(String operator, Object operand) throws Exception {
        int opcode;
        switch (operator) {
            case "!": opcode = 37; break;
            case "++": opcode = 48; break;
            default: throw new IllegalArgumentException("Unsupported unary operator: " + operator);
        }

        Method unaryMethod = treeMaker.getClass().getDeclaredMethod("Unary", 
                int.class, Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));
        return unaryMethod.invoke(treeMaker, opcode, operand);
    }

    public Object createBinaryExpression(Object left, String operator, Object right) throws Exception {
        int opcode;
        switch (operator) {
            case ">": opcode = 19; break;
            case "<": opcode = 17; break;
            case ">=": opcode = 20; break;
            case "<=": opcode = 18; break;
            case "==": opcode = 15; break;
            case "!=": opcode = 16; break;
            case "+": opcode = 13; break;
            case "-": opcode = 14; break;
            default: throw new IllegalArgumentException("Unsupported binary operator: " + operator);
        }

        Method binaryMethod = treeMaker.getClass().getDeclaredMethod("Binary", 
                int.class,
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));
        return binaryMethod.invoke(treeMaker, opcode, left, right);
    }

    public Object createIfStatement(Object condition, Object thenStatement) throws Exception {
        Method ifMethod = treeMaker.getClass().getDeclaredMethod("If", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCStatement"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCStatement"));
        return ifMethod.invoke(treeMaker, condition, thenStatement, null);
    }

    public Object createForStatement(Object init, Object condition, Object update, Object body) throws Exception {
        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        Method ofMethod = javacListClass.getDeclaredMethod("of", Object.class);
        Object initList = ofMethod.invoke(null, init);
        Object updateList = ofMethod.invoke(null, update);

        Method forLoopMethod = treeMaker.getClass().getDeclaredMethod("ForLoop", 
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCStatement"));
        return forLoopMethod.invoke(treeMaker, initList, condition, updateList, body);
    }

    public Object createArrayInitializer(String elementType, List<Object> elements) throws Exception {
        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        Method fromMethod = javacListClass.getDeclaredMethod("from", Object[].class);
        Object elementsList = fromMethod.invoke(null, new Object[]{elements.toArray()});

        Method newArrayMethod = treeMaker.getClass().getDeclaredMethod("NewArray", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.util.List"));
        
        Object elementTypeExpr = createQualifiedIdent(elementType);
        Method nilMethod = javacListClass.getDeclaredMethod("nil");
        Object emptyDims = nilMethod.invoke(null);

        return newArrayMethod.invoke(treeMaker, elementTypeExpr, emptyDims, elementsList);
    }

    public Object createClassLiteral(String className) throws Exception {
        Object classType = createQualifiedIdent(className);
        Method selectMethod = treeMaker.getClass().getDeclaredMethod("Select", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"), 
                Class.forName("com.sun.tools.javac.util.Name"));
        Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
        Object classNameObj = fromStringMethod.invoke(names, "class");
        return selectMethod.invoke(treeMaker, classType, classNameObj);
    }

    public Object createParameterArray(List<?> methodParams) throws Exception {
        List<Object> elements = new ArrayList<>();
        for (Object param : methodParams) {
            String name = extractParamName(param);
            elements.add(createIdent(name));
        }
        return createArrayInitializer("Object", elements);
    }

    private String extractParamName(Object param) {
        if (param == null) return "param";
        
        if (param instanceof String) {
            return param.toString();
        }
        
        try {
            Method getName = param.getClass().getMethod("getName");
            Object val = getName.invoke(param);
            if (val != null) return val.toString();
        } catch (Exception ignore) {}
        try {
            Method getParamName = param.getClass().getMethod("getParamName");
            Object val = getParamName.invoke(param);
            if (val != null) return val.toString();
        } catch (Exception ignore) {}

        return "param";
    }

    public Object createAddValueStatement(Object param) throws Exception {
        Object paramSourceAccess = createIdent("paramSource");
        Object addValueMethod = createFieldAccess(paramSourceAccess, "addValue");
        Object paramNameLiteral = createLiteral("param");
        Object paramValue = createIdent("value");
        Object addValueCall = createMethodCall(addValueMethod, paramNameLiteral, paramValue);
        return createExpressionStatement(addValueCall);
    }

    public Object createCollectionProcessing(Object param) throws Exception {
        return createIdent("collection");
    }

    public Object createManualRowMapper(String resultTypeClass, String columnMapping) throws Exception {
        return createIdent("manualRowMapper");
    }

    public Object createNestedRowMapper(String resultTypeClass, String columnMapping) throws Exception {
        return createIdent("nestedRowMapper");
    }

    public Object createVariable(String name, Object type) throws Exception {
        Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
        Object nameObj = fromStringMethod.invoke(names, name);

        Method varDefMethod = treeMaker.getClass().getDeclaredMethod("VarDef", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCModifiers"),
                Class.forName("com.sun.tools.javac.util.Name"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));

        Object modifiers = createModifiers(0L, null);
        return varDefMethod.invoke(treeMaker, modifiers, nameObj, type, null);
    }

    public Object createNewInstance(Object type) throws Exception {
        Method newClassMethod = treeMaker.getClass().getDeclaredMethod("NewClass", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCClassDecl"));

        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        Method nilMethod = javacListClass.getDeclaredMethod("nil");
        Object emptyList = nilMethod.invoke(null);

        return newClassMethod.invoke(treeMaker, null, emptyList, type, emptyList, null);
    }

    public Object createNewClass(Object type, Object[] args) throws Exception {
        Method newClassMethod = treeMaker.getClass().getDeclaredMethod("NewClass", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCClassDecl"));

        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        Method fromMethod = javacListClass.getDeclaredMethod("from", Object[].class);
        Object argsList = fromMethod.invoke(null, new Object[]{args});
        
        Method nilMethod = javacListClass.getDeclaredMethod("nil");
        Object emptyList = nilMethod.invoke(null);

        return newClassMethod.invoke(treeMaker, null, emptyList, type, argsList, null);
    }

    public Object createBeanPropertyRowMapper(String resultTypeClass) throws Exception {
        Object parameterizedRowMapperType = createParameterizedType("org.springframework.jdbc.core.BeanPropertyRowMapper", resultTypeClass);
        Object entityClassLiteral = createClassLiteral(resultTypeClass);
        return createNewClass(parameterizedRowMapperType, new Object[]{entityClassLiteral});
    }

    public Object createColumnMapRowMapper() throws Exception {
        Object rowMapperType = createQualifiedIdent("org.springframework.jdbc.core.ColumnMapRowMapper");
        return createNewInstance(rowMapperType);
    }
    
    public Object createParameterizedType(String baseTypeName, String paramTypeName) throws Exception {

        Object baseType = createQualifiedIdent(baseTypeName);
        Object paramType = createQualifiedIdent(paramTypeName);
        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        Method ofMethod;
        Object argsList;
        try {
            ofMethod = javacListClass.getDeclaredMethod("of", Object.class);
            argsList = ofMethod.invoke(null, paramType);
        } catch (NoSuchMethodException e) {
            Method fromMethod = javacListClass.getDeclaredMethod("from", Object[].class);
            argsList = fromMethod.invoke(null, new Object[]{new Object[]{paramType}});
        }
        Method typeApply = treeMaker.getClass().getDeclaredMethod("TypeApply",
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.util.List"));
        return typeApply.invoke(treeMaker, baseType, argsList);
    }

    public Object createParameterSourceCreation(List<?> methodParams) throws Exception {
        Object paramSourceType = createQualifiedIdent("org.springframework.jdbc.core.namedparam.MapSqlParameterSource");
        return createNewInstance(paramSourceType);
    }

    public Object createTypeCast(Object type, Object expression) throws Exception {
        Method typeCastMethod = treeMaker.getClass().getDeclaredMethod("TypeCast", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));
        return typeCastMethod.invoke(treeMaker, type, expression);
    }

    /**
     * Create ternary null check expression: result != null ? result.primitiveValue() : defaultValue
     */
    public Object createTernaryNullCheck(Object wrapperValue, String conversionMethod, String defaultValue) throws Exception {
        if (!astAvailable) {
            return wrapperValue;
        }
        
        try {
            // Create null check: wrapperValue != null
            Object nullLiteral = createLiteral((Object) null);
            Object notEqualsCondition = createBinaryExpression(wrapperValue, "!=", nullLiteral);
            
            // Create method call: wrapperValue.conversionMethod()
            Object conversionMethodAccess = createFieldAccess(wrapperValue, conversionMethod);
            Object conversionCall = createMethodCall(conversionMethodAccess);
            
            // Create default value literal (handle primitive literals properly)
            Object defaultValueLiteral = createPrimitiveLiteral(defaultValue);
            
            // Create ternary expression: condition ? trueExpr : falseExpr
            return createConditionalExpression(notEqualsCondition, conversionCall, defaultValueLiteral);
        } catch (Exception e) {
            return wrapperValue;
        }
    }

    public Object createConditionalExpression(Object condition, Object trueExpr, Object falseExpr) throws Exception {
        Method conditionalMethod = treeMaker.getClass().getDeclaredMethod("Conditional",
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"), 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));
        return conditionalMethod.invoke(treeMaker, condition, trueExpr, falseExpr);
    }

    public Object createPrimitiveLiteral(String defaultValue) throws Exception {
        if (!astAvailable) {
            return null;
        }

        try {

            Object actualValue;
            
            if ("0L".equals(defaultValue)) {
                actualValue = 0L;
            } else if ("0".equals(defaultValue)) {
                actualValue = 0;
            } else if ("0.0".equals(defaultValue)) {
                actualValue = 0.0;
            } else if ("0.0f".equals(defaultValue)) {
                actualValue = 0.0f;
            } else if ("false".equals(defaultValue)) {
                actualValue = false;
            } else if ("true".equals(defaultValue)) {
                actualValue = true;
            } else if ("(short) 0".equals(defaultValue)) {
                actualValue = (short) 0;
            } else if ("(byte) 0".equals(defaultValue)) {
                actualValue = (byte) 0;
            } else {
                return createLiteral(defaultValue);
            }
            
            Method literalMethod = treeMaker.getClass().getDeclaredMethod("Literal", Object.class);
            return literalMethod.invoke(treeMaker, actualValue);
        } catch (Exception e) {
            return createLiteral(defaultValue);
        }
    }


}
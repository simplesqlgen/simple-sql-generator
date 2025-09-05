package io.github.simplesqlgen.processor.ast;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * AST 조작을 위한 헬퍼 클래스
 * JavaC의 내부 API를 리플렉션으로 사용하여 코드 생성을 담당
 */
public class ASTHelper {
    
    // AST 조작 도구들 (리플렉션으로 접근)
    private Object trees;
    private Object treeMaker;
    private Object names;
    private Object context;
    private boolean astAvailable = false;

    // 필요한 클래스들 (리플렉션으로 로드)
    private Class<?> javacEnvClass;
    private Class<?> jcTreeClass;
    private Class<?> jcMethodDeclClass;
    private Class<?> jcClassDeclClass;
    private Class<?> jcBlockClass;
    private Class<?> jcReturnClass;
    private Class<?> jcLiteralClass;
    private Class<?> jcStatementClass;

    /**
     * AST 도구들을 초기화
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
     * AST가 사용 가능한지 확인
     */
    public boolean isASTAvailable() {
        return astAvailable;
    }

    /**
     * 모든 필요한 javac 클래스를 리플렉션으로 로드
     */
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

    /**
     * JavaC 처리 환경인지 확인
     */
    private boolean isJavacProcessingEnvironment(ProcessingEnvironment processingEnv) {
        return javacEnvClass.isInstance(processingEnv);
    }

    /**
     * 처리 환경에서 컨텍스트 추출
     */
    private Object extractContext(ProcessingEnvironment processingEnv) throws Exception {
        Field contextField = javacEnvClass.getDeclaredField("context");
        contextField.setAccessible(true);
        return contextField.get(processingEnv);
    }

    /**
     * AST 도구들 초기화
     */
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
     * 컴파일 단위 가져오기
     */
    public Object getCompilationUnit(Object treePath) throws Exception {
        Method getCompilationUnitMethod = treePath.getClass().getDeclaredMethod("getCompilationUnit");
        return getCompilationUnitMethod.invoke(treePath);
    }

    /**
     * 클래스 선언 가져오기
     */
    public Object getClassDecl(Object treePath) throws Exception {
        Object compilationUnit = getCompilationUnit(treePath);
        Method getTypeDeclsMethod = compilationUnit.getClass().getDeclaredMethod("getTypeDecls");
        List<?> typeDecls = (List<?>) getTypeDeclsMethod.invoke(compilationUnit);
        return typeDecls.get(0);
    }

    /**
     * 필요한 import들 추가
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
            // Import 추가 실패는 무시
        }
    }

    /**
     * 단일 import 추가 (누락된 경우)
     */
    private void addSingleImportIfMissing(Object compilationUnit, String importPath) throws Exception {
        try {
            Method getDefsMethod = compilationUnit.getClass().getDeclaredMethod("defs");
            Object existingDefs = getDefsMethod.invoke(compilationUnit);

            if (!hasImport(existingDefs, importPath)) {
                Object importDecl = createImportDecl(importPath);
                Method prependMethod = existingDefs.getClass().getDeclaredMethod("prepend", Object.class);
                Object newDefs = prependMethod.invoke(existingDefs, importDecl);
                
                // defs 필드에 직접 할당
                Field defsField = compilationUnit.getClass().getDeclaredField("defs");
                defsField.setAccessible(true);
                defsField.set(compilationUnit, newDefs);
            }
        } catch (Exception e) {
            // Import 추가 실패는 무시 (기존 동작 유지)
        }
    }

    /**
     * Import 선언 생성
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
                throw new RuntimeException("Import 생성 실패: " + qualifiedName, ex);
            }
        }
    }

    /**
     * Qualified identifier 생성
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

    /**
     * Package 선언인지 확인
     */
    private boolean isPackageDecl(Object def) throws Exception {
        return def.getClass().getSimpleName().equals("JCPackageDecl");
    }

    /**
     * Import 선언인지 확인
     */
    private boolean isImportDecl(Object def) throws Exception {
        return def.getClass().getSimpleName().equals("JCImport");
    }

    /**
     * 메서드들을 변환
     */
    public int transformMethods(Object classDecl, TypeElement classElement, TypeMirror entityType,
                               String tableName, Object entityInfo) throws Exception {
        Method getMembersMethod = classDecl.getClass().getDeclaredMethod("getMembers");
        Object membersList = getMembersMethod.invoke(classDecl);

        Object newMembersList = createTransformedMembersList(membersList, classElement, entityType, tableName, entityInfo);

        // setMembers 메서드 대신 필드 직접 접근 시도
        try {
            Method setMembersMethod = classDecl.getClass().getDeclaredMethod("setMembers", 
                    Class.forName("com.sun.tools.javac.util.List"));
            setMembersMethod.invoke(classDecl, newMembersList);
        } catch (NoSuchMethodException e) {
            // 필드 직접 접근으로 대체
            try {
                Field defsField = classDecl.getClass().getDeclaredField("defs");
                defsField.setAccessible(true);
                defsField.set(classDecl, newMembersList);
            } catch (Exception ex) {
                System.out.println("⚠️ 클래스 멤버 설정 실패: " + ex.getMessage());
            }
        }

        return 0; // 변환된 메서드 수 반환
    }

    /**
     * Import가 존재하는지 확인
     */
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
                            // 무시
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 무시
        }
        return false;
    }

    /**
     * JdbcTemplate 생성자 주입 (개선된 버전)
     */
    public void injectJdbcTemplateConstructor(Object classDecl) throws Exception {
        try {
            Method getMembersMethod = classDecl.getClass().getDeclaredMethod("getMembers");
            Object membersList = getMembersMethod.invoke(classDecl);

            if (!hasJdbcTemplateField(membersList) && !hasConstructorWithJdbcTemplate(membersList)) {
                System.out.println("🔧 필드와 생성자 추가 시작");
                
                Method prependMethod = membersList.getClass().getDeclaredMethod("prepend", Object.class);
                
                // 1. final 필드 추가 (생성자 주입용)
                System.out.println("🔧 final 필드 추가 중...");
                Object jdbcTemplateField = createFinalField("JdbcTemplate", "jdbcTemplate");
                membersList = prependMethod.invoke(membersList, jdbcTemplateField);
                System.out.println("✅ jdbcTemplate 필드 추가 완료");

                Object namedJdbcTemplateField = createFinalField("NamedParameterJdbcTemplate", "namedParameterJdbcTemplate");
                membersList = prependMethod.invoke(membersList, namedJdbcTemplateField);
                System.out.println("✅ namedParameterJdbcTemplate 필드 추가 완료");

                // 2. 생성자 추가 시도 (실패해도 계속 진행)
                try {
                    System.out.println("🔧 생성자 추가 시도 중...");
                    Object constructor = createSafeJdbcTemplateConstructor();
                    if (constructor != null) {
                        membersList = prependMethod.invoke(membersList, constructor);
                        System.out.println("✅ 생성자 추가 성공");
                    } else {
                        System.out.println("⚠️ 생성자 생성 실패, @Autowired 필드만 사용");
                    }
                } catch (Exception constructorEx) {
                    System.out.println("⚠️ 생성자 추가 실패, @Autowired 필드만 사용: " + constructorEx.getMessage());
                }

                // 3. 클래스에 멤버 리스트 설정
                updateClassMembers(classDecl, membersList);
                
                System.out.println("✅ 필드와 생성자 추가 완료");
            } else {
                System.out.println("⚠️ 이미 JdbcTemplate 필드 또는 생성자가 존재함");
            }
        } catch (Exception e) {
            System.out.println("❌ 의존성 주입 설정 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            // 완전 실패해도 빌드는 계속 진행
        }
    }

    /**
     * @Autowired 필드만 주입 (생성자 없이)
     */
    public void injectAutowiredFields(Object classDecl) throws Exception {
        try {
            Method getMembersMethod = classDecl.getClass().getDeclaredMethod("getMembers");
            Object membersList = getMembersMethod.invoke(classDecl);

            if (!hasJdbcTemplateField(membersList)) {
                System.out.println("🔧 @Autowired 필드 추가 시작");
                
                Method prependMethod = membersList.getClass().getDeclaredMethod("prepend", Object.class);
                
                // @Autowired 필드 추가
                System.out.println("🔧 @Autowired 필드 추가 중...");
                Object jdbcTemplateField = createAutowiredField("JdbcTemplate", "jdbcTemplate");
                membersList = prependMethod.invoke(membersList, jdbcTemplateField);
                System.out.println("✅ @Autowired jdbcTemplate 필드 추가 완료");

                Object namedJdbcTemplateField = createAutowiredField("NamedParameterJdbcTemplate", "namedParameterJdbcTemplate");
                membersList = prependMethod.invoke(membersList, namedJdbcTemplateField);
                System.out.println("✅ @Autowired namedParameterJdbcTemplate 필드 추가 완료");

                // 클래스에 멤버 리스트 설정
                updateClassMembers(classDecl, membersList);
                
                System.out.println("✅ @Autowired 필드 추가 완료");
            } else {
                System.out.println("⚠️ 이미 JdbcTemplate 필드가 존재함");
            }
        } catch (Exception e) {
            System.out.println("❌ @Autowired 필드 주입 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            // 완전 실패해도 빌드는 계속 진행
        }
    }

    /**
     * 간단한 @Autowired 필드 생성 (어노테이션 없이)
     */
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

    /**
     * @Autowired 필드 생성
     */
    private Object createAutowiredField(String fieldType, String fieldName) throws Exception {
        Object autowiredType = createQualifiedIdent("org.springframework.beans.factory.annotation.Autowired");
        Object autowiredAnnotation = createAnnotation(autowiredType);

        long privateFlag = 1L << 1;

        Object annotations;
        Object modifiers;
        
        // 어노테이션 생성 실패 시 어노테이션 없는 필드로 생성
        if (autowiredAnnotation != null) {
            System.out.println("✅ @Autowired 어노테이션 생성 성공");
            annotations = createSingletonList(autowiredAnnotation);
            modifiers = createModifiers(privateFlag, annotations);
        } else {
            System.out.println("⚠️ @Autowired 어노테이션 생성 실패, 어노테이션 없는 필드로 생성");
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

    /**
     * 어노테이션 생성 (안정화된 버전)
     */
    public Object createAnnotation(Object annotationType) throws Exception {
        try {
            System.out.println("🔧 @Autowired 어노테이션 생성 시작");
            
            Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
            Method nilMethod = javacListClass.getDeclaredMethod("nil");
            Object emptyArguments = nilMethod.invoke(null);
            
            // Java 17 호환 방법 1: TreeMaker.Annotation 시도
            try {
                Method annotationMethod = treeMaker.getClass().getDeclaredMethod("Annotation", 
                        Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                        javacListClass);
                Object annotation = annotationMethod.invoke(treeMaker, annotationType, emptyArguments);
                System.out.println("✅ @Autowired 어노테이션 생성 성공 (방법1): " + annotation.getClass().getSimpleName());
                return annotation;
            } catch (Exception e1) {
                System.out.println("⚠️ 방법1 실패: " + e1.getMessage());
            }
            
            // Java 17 호환 방법 2: 다른 시그니처 시도
            try {
                Method annotationMethod = treeMaker.getClass().getDeclaredMethod("Annotation", 
                        Class.forName("com.sun.tools.javac.tree.JCTree"),
                        javacListClass);
                Object annotation = annotationMethod.invoke(treeMaker, annotationType, emptyArguments);
                System.out.println("✅ @Autowired 어노테이션 생성 성공 (방법2): " + annotation.getClass().getSimpleName());
                return annotation;
            } catch (Exception e2) {
                System.out.println("⚠️ 방법2 실패: " + e2.getMessage());
            }
            
            // Java 17 호환 방법 3: 가장 기본적인 시그니처
            try {
                Method annotationMethod = treeMaker.getClass().getDeclaredMethod("Annotation", 
                        Object.class, Object.class);
                Object annotation = annotationMethod.invoke(treeMaker, annotationType, emptyArguments);
                System.out.println("✅ @Autowired 어노테이션 생성 성공 (방법3): " + annotation.getClass().getSimpleName());
                return annotation;
            } catch (Exception e3) {
                System.out.println("⚠️ 방법3 실패: " + e3.getMessage());
            }
            
            // 모든 방법이 실패한 경우 사용 가능한 메서드 목록 출력
            System.out.println("🔍 TreeMaker에서 사용 가능한 메서드들:");
            Method[] methods = treeMaker.getClass().getDeclaredMethods();
            for (Method method : methods) {
                if (method.getName().toLowerCase().contains("annotation")) {
                    System.out.println("  " + method.getName() + " - " + java.util.Arrays.toString(method.getParameterTypes()));
                }
            }
            
            System.out.println("⚠️ 모든 어노테이션 생성 방법 실패, null 반환");
            return null;
            
        } catch (Exception e) {
            System.out.println("❌ 어노테이션 생성 중 심각한 오류: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 수정자 생성
     */
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

    /**
     * 단일 요소 리스트 생성
     */
    public Object createSingletonList(Object element) throws Exception {
        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        Method ofMethod = javacListClass.getDeclaredMethod("of", Object.class);
        return ofMethod.invoke(null, element);
    }

    /**
     * JdbcTemplate 필드가 존재하는지 확인
     */
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

    /**
     * JdbcTemplate을 받는 생성자가 존재하는지 확인
     */
    private boolean hasConstructorWithJdbcTemplate(Object membersList) throws Exception {
        if (membersList instanceof Iterable) {
            for (Object member : (Iterable<?>) membersList) {
                if (member.getClass().getSimpleName().equals("JCMethodDecl")) {
                    Method getNameMethod = member.getClass().getDeclaredMethod("getName");
                    Object name = getNameMethod.invoke(member);
                    String nameStr = name.toString();
                    if ("<init>".equals(nameStr)) {
                        // 생성자의 파라미터 확인
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

    /**
     * final 필드 생성
     */
    private Object createFinalField(String fieldType, String fieldName) throws Exception {
        long privateFlag = 1L << 1;  // private
        long finalFlag = 1L << 4;    // final
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

    /**
     * JdbcTemplate 생성자 생성
     */
    private Object createJdbcTemplateConstructor() throws Exception {
        try {
            System.out.println("🔧 생성자 생성 시작");
            
            // 생성자 파라미터 생성
            System.out.println("🔧 파라미터 생성 중...");
            Object jdbcTemplateParam = createConstructorParameter("JdbcTemplate", "jdbcTemplate");
            Object namedJdbcTemplateParam = createConstructorParameter("NamedParameterJdbcTemplate", "namedParameterJdbcTemplate");
            System.out.println("✅ 파라미터 생성 완료");
            
            Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
            Method ofMethod = javacListClass.getDeclaredMethod("of", Object.class, Object.class);
            Object paramsList = ofMethod.invoke(null, jdbcTemplateParam, namedJdbcTemplateParam);
            System.out.println("✅ 파라미터 리스트 생성 완료");

            // 생성자 본문 생성 (필드 할당)
            System.out.println("🔧 생성자 본문 생성 중...");
            Object jdbcTemplateAssignment = createFieldAssignment("jdbcTemplate", "jdbcTemplate");
            Object namedJdbcTemplateAssignment = createFieldAssignment("namedParameterJdbcTemplate", "namedParameterJdbcTemplate");
            
            Object assignmentsList = ofMethod.invoke(null, jdbcTemplateAssignment, namedJdbcTemplateAssignment);
            Object constructorBody = createConstructorBody(assignmentsList);
            System.out.println("✅ 생성자 본문 생성 완료");

            // 생성자 메서드 생성
            System.out.println("🔧 생성자 메서드 생성 중...");
            long publicFlag = 1L << 0;  // public
            Object modifiers = createModifiers(publicFlag, null);
            
            Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
            Object constructorName = fromStringMethod.invoke(names, "<init>");
            
            // void 타입 대신 null 사용 (생성자는 반환 타입이 없음)
            Object returnType = null;
            
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

            Object constructor = methodDefMethod.invoke(treeMaker, modifiers, constructorName, returnType, 
                    emptyTypeParams, paramsList, emptyThrows, constructorBody, null);
            System.out.println("✅ 생성자 메서드 생성 완료");
            
            return constructor;
            
        } catch (Exception e) {
            System.err.println("❌ 생성자 생성 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 생성자 파라미터 생성 (플래그 문제 해결)
     */
    private Object createConstructorParameter(String paramType, String paramName) throws Exception {
        try {
            System.out.println("🔧 파라미터 생성: " + paramType + " " + paramName);
            
            Object type;
            if (paramType.equals("JdbcTemplate")) {
                type = createQualifiedIdent("org.springframework.jdbc.core.JdbcTemplate");
            } else {
                type = createQualifiedIdent("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate");
            }

            Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
            Object paramNameObj = fromStringMethod.invoke(names, paramName);
            
            // PARAMETER 플래그 설정 시도
            long parameterFlag = getParameterFlag();
            Object modifiers = createModifiers(parameterFlag, null);
            System.out.println("✅ 파라미터 플래그 설정: " + parameterFlag);

            Method varDefMethod = treeMaker.getClass().getDeclaredMethod("VarDef", 
                    Class.forName("com.sun.tools.javac.tree.JCTree$JCModifiers"),
                    Class.forName("com.sun.tools.javac.util.Name"),
                    Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                    Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));

            Object param = varDefMethod.invoke(treeMaker, modifiers, paramNameObj, type, null);
            System.out.println("✅ 파라미터 생성 완료");
            return param;
            
        } catch (Exception e) {
            System.err.println("❌ 파라미터 생성 실패 (" + paramType + " " + paramName + "): " + e.getMessage());
            throw e;
        }
    }

    /**
     * PARAMETER 플래그 값 가져오기 (안전한 방식)
     */
    private long getParameterFlag() {
        try {
            // Flags.PARAMETER 값 찾기 시도
            Class<?> flagsClass = Class.forName("com.sun.tools.javac.code.Flags");
            Field parameterField = flagsClass.getDeclaredField("PARAMETER");
            long flag = parameterField.getLong(null);
            System.out.println("✅ PARAMETER 플래그 찾음: " + flag);
            return flag;
        } catch (Exception e) {
            System.out.println("⚠️ PARAMETER 플래그를 찾을 수 없음, fallback 사용: " + e.getMessage());
            // fallback - 일반적인 PARAMETER 플래그 값
            long flag = 1L << 8; // 0x100
            System.out.println("⚠️ fallback PARAMETER 플래그 사용: " + flag);
            return flag;
        }
    }

    /**
     * 안전한 생성자 생성 (실패해도 null 반환)
     */
    private Object createSafeJdbcTemplateConstructor() {
        try {
            return createJdbcTemplateConstructor();
        } catch (Exception e) {
            System.out.println("⚠️ 생성자 생성 실패, null 반환: " + e.getMessage());
            return null;
        }
    }

    /**
     * 클래스 멤버 업데이트 (안전한 방법)
     */
    private void updateClassMembers(Object classDecl, Object membersList) {
        try {
            Method setMembersMethod = classDecl.getClass().getDeclaredMethod("setMembers", 
                    Class.forName("com.sun.tools.javac.util.List"));
            setMembersMethod.invoke(classDecl, membersList);
            System.out.println("✅ setMembers 메서드로 업데이트 완료");
        } catch (NoSuchMethodException e) {
            try {
                Field defsField = classDecl.getClass().getDeclaredField("defs");
                defsField.setAccessible(true);
                defsField.set(classDecl, membersList);
                System.out.println("✅ 필드 직접 접근으로 업데이트 완료");
            } catch (Exception ex) {
                System.out.println("❌ 클래스 멤버 업데이트 실패: " + ex.getMessage());
            }
        } catch (Exception e) {
            System.out.println("❌ 클래스 멤버 업데이트 실패: " + e.getMessage());
        }
    }

    /**
     * 필드 할당문 생성 (this.field = param)
     */
    private Object createFieldAssignment(String fieldName, String paramName) throws Exception {
        try {
            System.out.println("🔧 필드 할당문 생성: this." + fieldName + " = " + paramName);
            
            Object thisAccess = createIdent("this");
            System.out.println("✅ this 접근 생성 완료");
            
            Object fieldAccess = createFieldAccess(thisAccess, fieldName);
            System.out.println("✅ 필드 접근 생성 완료");
            
            Object paramAccess = createIdent(paramName);
            System.out.println("✅ 파라미터 접근 생성 완료");
            
            Object assignment = createAssignment(fieldAccess, paramAccess);
            System.out.println("✅ 할당문 생성 완료");
            
            Object statement = createExpressionStatement(assignment);
            System.out.println("✅ 표현식 문장 생성 완료");
            
            return statement;
            
        } catch (Exception e) {
            System.err.println("❌ 필드 할당문 생성 실패 (" + fieldName + " = " + paramName + "): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 생성자 본문 생성
     */
    private Object createConstructorBody(Object statementsList) throws Exception {
        Method blockMethod = treeMaker.getClass().getDeclaredMethod("Block", 
                long.class, Class.forName("com.sun.tools.javac.util.List"));
        return blockMethod.invoke(treeMaker, 0L, statementsList);
    }

    /**
     * 변환된 멤버 리스트 생성
     */
    private Object createTransformedMembersList(Object originalList, TypeElement classElement,
                                               TypeMirror entityType, String tableName, Object entityInfo) throws Exception {
        // 이 메서드는 메인 SqlProcessor에서 호출할 콜백을 받도록 구현해야 함
        return originalList;
    }

    /**
     * 메서드 호출 생성
     */
    public Object createMethodCall(Object method, Object... args) throws Exception {
        Method applyMethod = treeMaker.getClass().getDeclaredMethod("Apply", 
                Class.forName("com.sun.tools.javac.util.List"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.util.List"));

        // 빈 타입 인자 리스트
        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        Method nilMethod = javacListClass.getDeclaredMethod("nil");
        Object emptyTypeArgs = nilMethod.invoke(null);

        // 인자 리스트 생성
        Object argsList;
        if (args.length == 0) {
            argsList = nilMethod.invoke(null);
        } else {
            Method fromMethod = javacListClass.getDeclaredMethod("from", Object[].class);
            argsList = fromMethod.invoke(null, new Object[]{args});
        }

        return applyMethod.invoke(treeMaker, emptyTypeArgs, method, argsList);
    }

    /**
     * 필드 액세스 생성 (객체.필드)
     */
    public Object createFieldAccess(Object base, String fieldName) throws Exception {
        Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
        Object fieldNameObj = fromStringMethod.invoke(names, fieldName);
        
        Method selectMethod = treeMaker.getClass().getDeclaredMethod("Select", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"), 
                Class.forName("com.sun.tools.javac.util.Name"));
        return selectMethod.invoke(treeMaker, base, fieldNameObj);
    }

    /**
     * 필드 액세스 생성 (문자열 기반)
     */
    public Object createFieldAccess(String baseName, String fieldName) throws Exception {
        Object baseIdent = createIdent(baseName);
        return createFieldAccess(baseIdent, fieldName);
    }

    /**
     * 식별자 생성
     */
    public Object createIdent(String name) throws Exception {
        Method identMethod = treeMaker.getClass().getDeclaredMethod("Ident", 
                Class.forName("com.sun.tools.javac.util.Name"));
        Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
        Object nameObj = fromStringMethod.invoke(names, name);
        return identMethod.invoke(treeMaker, nameObj);
    }

    /**
     * 리터럴 생성
     */
    public Object createLiteral(String value) throws Exception {
        Method literalMethod = treeMaker.getClass().getDeclaredMethod("Literal", Object.class);
        return literalMethod.invoke(treeMaker, value);
    }

    /**
     * 표현식 문장 생성
     */
    public Object createExpressionStatement(Object expr) throws Exception {
        Method execMethod = treeMaker.getClass().getDeclaredMethod("Exec", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));
        return execMethod.invoke(treeMaker, expr);
    }


    /**
     * TreePath 가져오기
     */
    public Object getTreePath(javax.lang.model.element.Element element) throws Exception {
        Method getPathMethod = trees.getClass().getDeclaredMethod("getPath", javax.lang.model.element.Element.class);
        return getPathMethod.invoke(trees, element);
    }

    /**
     * 리터럴 생성 (일반 객체)
     */
    public Object createLiteral(Object value) throws Exception {
        Method literalMethod = treeMaker.getClass().getDeclaredMethod("Literal", Object.class);
        return literalMethod.invoke(treeMaker, value);
    }

    /**
     * 변수 생성 (이름과 타입으로)
     */
    public Object createVariable(String name, String type) throws Exception {
        return createIdent(name);
    }

    /**
     * 변수 생성 (이름만)
     */
    public Object createVariable(String name) throws Exception {
        return createIdent(name);
    }

    /**
     * 할당문 생성
     */
    public Object createAssignment(Object left, Object right) throws Exception {
        Method assignMethod = treeMaker.getClass().getDeclaredMethod("Assign", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));
        return assignMethod.invoke(treeMaker, left, right);
    }

    /**
     * 블록 생성
     */
    public Object createBlock(List<Object> statements) throws Exception {
        Class<?> javacListClass = Class.forName("com.sun.tools.javac.util.List");
        Method fromMethod = javacListClass.getDeclaredMethod("from", Object[].class);
        Object statementsList = fromMethod.invoke(null, new Object[]{statements.toArray()});

        Method blockMethod = treeMaker.getClass().getDeclaredMethod("Block", 
                long.class, Class.forName("com.sun.tools.javac.util.List"));
        return blockMethod.invoke(treeMaker, 0L, statementsList);
    }

    /**
     * 단일 문장으로 블록 생성
     */
    public Object createBlockFromStatement(Object statement) throws Exception {
        List<Object> statements = new ArrayList<>();
        statements.add(statement);
        return createBlock(statements);
    }

    /**
     * Return 문 생성
     */
    public Object createReturnStatement(Object expr) throws Exception {
        Method returnMethod = treeMaker.getClass().getDeclaredMethod("Return", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));
        return returnMethod.invoke(treeMaker, expr);
    }

    /**
     * StringBuilder 생성
     */
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

    /**
     * 단항 연산자 생성
     */
    public Object createUnaryExpression(String operator, Object operand) throws Exception {
        int opcode;
        switch (operator) {
            case "!": opcode = 37; break; // NOT
            case "++": opcode = 48; break; // PREINC
            default: throw new IllegalArgumentException("Unsupported unary operator: " + operator);
        }

        Method unaryMethod = treeMaker.getClass().getDeclaredMethod("Unary", 
                int.class, Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));
        return unaryMethod.invoke(treeMaker, opcode, operand);
    }

    /**
     * 이항 연산자 생성
     */
    public Object createBinaryExpression(Object left, String operator, Object right) throws Exception {
        int opcode;
        switch (operator) {
            case ">": opcode = 19; break; // GT
            case "<": opcode = 17; break; // LT
            case ">=": opcode = 20; break; // GE
            case "<=": opcode = 18; break; // LE
            case "==": opcode = 15; break; // EQ
            case "!=": opcode = 16; break; // NE
            case "+": opcode = 13; break; // PLUS
            case "-": opcode = 14; break; // MINUS
            default: throw new IllegalArgumentException("Unsupported binary operator: " + operator);
        }

        Method binaryMethod = treeMaker.getClass().getDeclaredMethod("Binary", 
                int.class,
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));
        return binaryMethod.invoke(treeMaker, opcode, left, right);
    }

    /**
     * If 문 생성
     */
    public Object createIfStatement(Object condition, Object thenStatement) throws Exception {
        Method ifMethod = treeMaker.getClass().getDeclaredMethod("If", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCStatement"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCStatement"));
        return ifMethod.invoke(treeMaker, condition, thenStatement, null);
    }

    /**
     * For 문 생성
     */
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

    /**
     * 배열 초기화 생성
     */
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

    /**
     * 클래스 리터럴 생성
     */
    public Object createClassLiteral(String className) throws Exception {
        Object classType = createQualifiedIdent(className);
        Method selectMethod = treeMaker.getClass().getDeclaredMethod("Select", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"), 
                Class.forName("com.sun.tools.javac.util.Name"));
        Method fromStringMethod = names.getClass().getDeclaredMethod("fromString", String.class);
        Object classNameObj = fromStringMethod.invoke(names, "class");
        return selectMethod.invoke(treeMaker, classType, classNameObj);
    }

    /**
     * 파라미터 배열 생성
     */
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
        // fallback: toString()은 식별자로 부적합할 수 있으므로 안전한 이름 생성
        return "param";
    }

    /**
     * addValue 문장 생성
     */
    public Object createAddValueStatement(Object param) throws Exception {
        Object paramSourceAccess = createIdent("paramSource");
        Object addValueMethod = createFieldAccess(paramSourceAccess, "addValue");
        Object paramNameLiteral = createLiteral("param");
        Object paramValue = createIdent("value");
        Object addValueCall = createMethodCall(addValueMethod, paramNameLiteral, paramValue);
        return createExpressionStatement(addValueCall);
    }

    /**
     * 컬렉션 처리 생성
     */
    public Object createCollectionProcessing(Object param) throws Exception {
        return createIdent("collection");
    }

    /**
     * 수동 RowMapper 생성
     */
    public Object createManualRowMapper(String resultTypeClass, String columnMapping) throws Exception {
        return createIdent("manualRowMapper");
    }

    /**
     * 중첩 RowMapper 생성
     */
    public Object createNestedRowMapper(String resultTypeClass, String columnMapping) throws Exception {
        return createIdent("nestedRowMapper");
    }

    /**
     * 변수 선언 생성 (타입과 함께)
     */
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

    /**
     * 새로운 인스턴스 생성
     */
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

    /**
     * 새로운 클래스 생성 (인수와 함께)
     */
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

    /**
     * BeanPropertyRowMapper 생성 - 제네릭 타입 명시적 설정
     */
    public Object createBeanPropertyRowMapper(String resultTypeClass) throws Exception {
        // new BeanPropertyRowMapper<EntityClass>(EntityClass.class) 형태로 생성
        Object rowMapperType = createParameterizedType("org.springframework.jdbc.core.BeanPropertyRowMapper", resultTypeClass);
        Object entityClassLiteral = createClassLiteral(resultTypeClass);
        return createNewClass(rowMapperType, new Object[]{entityClassLiteral});
    }
    
    /**
     * 제네릭 타입 생성
     */
    public Object createParameterizedType(String baseTypeName, String paramTypeName) throws Exception {
        // Create a parameterized type: Base<Param>
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

    /**
     * MapSqlParameterSource 생성
     */
    public Object createParameterSourceCreation(List<?> methodParams) throws Exception {
        Object paramSourceType = createQualifiedIdent("org.springframework.jdbc.core.namedparam.MapSqlParameterSource");
        return createNewInstance(paramSourceType);
    }

    /**
     * 타입 캐스팅 생성
     */
    public Object createTypeCast(Object type, Object expression) throws Exception {
        Method typeCastMethod = treeMaker.getClass().getDeclaredMethod("TypeCast", 
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"),
                Class.forName("com.sun.tools.javac.tree.JCTree$JCExpression"));
        return typeCastMethod.invoke(treeMaker, type, expression);
    }


}
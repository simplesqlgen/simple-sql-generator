package io.github.simplesqlgen.permit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Lombok 방식으로 JDK 모듈 시스템을 우회하는 클래스
 * Java 9+ 모듈 시스템에서 내부 API 접근을 허용
 */
public class Permit {

    private static boolean initialized = false;

    /**
     * 모든 javac 패키지에 대한 접근 권한 한번에 설정
     */
    public static void permitAllJavacPackages() {
        if (initialized) return;

        System.out.println("🔓 Permit: JDK 모듈 시스템 우회 시작...");

        String[] javacPackages = {
                "com.sun.tools.javac.processing",
                "com.sun.tools.javac.tree",
                "com.sun.tools.javac.util",
                "com.sun.tools.javac.code",

                // ✅ 추가 필요 패키지들
                "com.sun.tools.javac.api",
                "com.sun.tools.javac.comp",
                "com.sun.tools.javac.parser",
                "com.sun.tools.javac.main",
                "com.sun.tools.javac.model",

                // com.sun.source 패키지들
                "com.sun.source.util",
                "com.sun.source.tree",

                // 추가 유틸리티
                "com.sun.tools.javac.jvm",
                "com.sun.tools.javac.file"
        };

        boolean allSuccess = true;
        for (String pkg : javacPackages) {
            if (!permitPackageAccess(pkg)) {
                allSuccess = false;
            }
        }

        initialized = true;

        if (allSuccess) {
            System.out.println("✅ Permit: 모든 javac 패키지 접근 허용 완료");
        } else {
            System.out.println("⚠️  Permit: 일부 패키지 접근 허용 실패 (부분적 성공)");
        }
    }

    /**
     * 특정 패키지에 대한 접근 권한 설정
     */
    public static boolean permitPackageAccess(String packageName) {
        try {
            System.out.println("🔓 Permit: " + packageName + " 접근 허용 중...");

            // 1. 현재 모듈과 대상 모듈 찾기
            Module currentModule = Permit.class.getModule();
            Module targetModule = findModuleByPackage(packageName);

            if (targetModule == null) {
                System.out.println("📝 Permit: " + packageName + " - 대상 모듈을 찾을 수 없음");
                return false;
            }

            if (targetModule.equals(currentModule)) {
                System.out.println("📝 Permit: " + packageName + " - 같은 모듈이므로 스킵");
                return true;
            }

            // 2. exports 및 opens 설정
            boolean exportsSuccess = addExports(targetModule, packageName, currentModule);
            boolean opensSuccess = addOpens(targetModule, packageName, currentModule);

            if (exportsSuccess && opensSuccess) {
                System.out.println("✅ Permit: " + packageName + " 접근 허용 완료");
                return true;
            } else {
                System.out.println("⚠️  Permit: " + packageName + " 부분적 성공");
                return false;
            }

        } catch (Exception e) {
            System.out.println("❌ Permit 실패 (" + packageName + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * 패키지에 해당하는 모듈 찾기
     */
    private static Module findModuleByPackage(String packageName) {
        try {
            // jdk.compiler 모듈의 대표 클래스들로 시도
            String[] testClasses = {
                    "com.sun.tools.javac.processing.JavacProcessingEnvironment",
                    "com.sun.tools.javac.tree.TreeMaker",
                    "com.sun.tools.javac.util.Names",
                    "com.sun.source.util.Trees"
            };

            for (String className : testClasses) {
                try {
                    Class<?> testClass = Class.forName(className);
                    return testClass.getModule();
                } catch (ClassNotFoundException e) {
                    // 다음 클래스 시도
                }
            }

            return null;

        } catch (Exception e) {
            System.out.println("📝 모듈 찾기 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * Module.implAddExports 호출
     */
    private static boolean addExports(Module sourceModule, String packageName, Module targetModule) {
        try {
            Method implAddExportsMethod = Module.class.getDeclaredMethod(
                    "implAddExports", String.class, Module.class);

            forceSetAccessible(implAddExportsMethod);
            implAddExportsMethod.invoke(sourceModule, packageName, targetModule);

            System.out.println("  📤 exports 추가 완료: " + packageName);
            return true;

        } catch (Exception e) {
            System.out.println("  ❌ exports 추가 실패: " + e.getMessage());
            return false;
        }
    }

    /**
     * Module.implAddOpens 호출
     */
    private static boolean addOpens(Module sourceModule, String packageName, Module targetModule) {
        try {
            Method implAddOpensMethod = Module.class.getDeclaredMethod(
                    "implAddOpens", String.class, Module.class);

            forceSetAccessible(implAddOpensMethod);
            implAddOpensMethod.invoke(sourceModule, packageName, targetModule);

            System.out.println("  🔓 opens 추가 완료: " + packageName);
            return true;

        } catch (Exception e) {
            System.out.println("  ❌ opens 추가 실패: " + e.getMessage());
            return false;
        }
    }

    /**
     * setAccessible 강제 우회 (Lombok 방식)
     */
    private static void forceSetAccessible(Object obj) throws Exception {
        try {
            // 일반적인 setAccessible 시도
            if (obj instanceof Method) {
                ((Method) obj).setAccessible(true);
            } else if (obj instanceof Field) {
                ((Field) obj).setAccessible(true);
            }

        } catch (Exception e) {
            // Plan B: Unsafe 사용
            System.out.println("  📝 일반 setAccessible 실패, Unsafe 시도...");
            tryUnsafeSetAccessible(obj);
        }
    }

    /**
     * Unsafe를 사용한 setAccessible 우회
     */
    private static void tryUnsafeSetAccessible(Object obj) throws Exception {
        try {
            // sun.misc.Unsafe 접근
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            Object unsafe = theUnsafeField.get(null);

            // AccessibleObject.override 필드 조작
            Class<?> accessibleObjectClass = Class.forName("java.lang.reflect.AccessibleObject");
            Field overrideField = accessibleObjectClass.getDeclaredField("override");

            Method objectFieldOffsetMethod = unsafeClass.getDeclaredMethod("objectFieldOffset", Field.class);
            long offset = (Long) objectFieldOffsetMethod.invoke(unsafe, overrideField);

            Method putBooleanMethod = unsafeClass.getDeclaredMethod("putBoolean", Object.class, long.class, boolean.class);
            putBooleanMethod.invoke(unsafe, obj, offset, true);

            System.out.println("  ✅ Unsafe setAccessible 성공");

        } catch (Exception unsafeException) {
            System.out.println("  ❌ Unsafe setAccessible도 실패: " + unsafeException.getMessage());
            throw new RuntimeException("모든 setAccessible 우회 방법 실패", unsafeException);
        }
    }
}
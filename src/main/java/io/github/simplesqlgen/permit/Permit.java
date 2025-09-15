package io.github.simplesqlgen.permit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Class that bypasses JDK module system in Lombok style
 * Allows access to internal APIs in Java 9+ module system
 */
public class Permit {

    private static boolean initialized = false;

    /**
     * Set access permissions for all javac packages at once
     */
    public static void permitAllJavacPackages() {
        if (initialized) return;

        String[] javacPackages = {
                "com.sun.tools.javac.processing",
                "com.sun.tools.javac.tree",
                "com.sun.tools.javac.util",
                "com.sun.tools.javac.code",


                "com.sun.tools.javac.api",
                "com.sun.tools.javac.comp",
                "com.sun.tools.javac.parser",
                "com.sun.tools.javac.main",
                "com.sun.tools.javac.model",


                "com.sun.source.util",
                "com.sun.source.tree",


                "com.sun.tools.javac.jvm",
                "com.sun.tools.javac.file"
        };

        for (String pkg : javacPackages) {
            permitPackageAccess(pkg);
        }

        initialized = true;
    }

    /**
     * Set access permissions for specific package
     */
    public static boolean permitPackageAccess(String packageName) {
        try {

            Module currentModule = Permit.class.getModule();
            Module targetModule = findModuleByPackage(packageName);

            if (targetModule == null) {
                return false;
            }

            if (targetModule.equals(currentModule)) {
                return true;
            }


            boolean exportsSuccess = addExports(targetModule, packageName, currentModule);
            boolean opensSuccess = addOpens(targetModule, packageName, currentModule);

            return exportsSuccess && opensSuccess;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Find module corresponding to package
     */
    private static Module findModuleByPackage(String packageName) {
        try {

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

                }
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Call Module.implAddExports
     */
    private static boolean addExports(Module sourceModule, String packageName, Module targetModule) {
        try {
            Method implAddExportsMethod = Module.class.getDeclaredMethod(
                    "implAddExports", String.class, Module.class);

            forceSetAccessible(implAddExportsMethod);
            implAddExportsMethod.invoke(sourceModule, packageName, targetModule);

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Call Module.implAddOpens
     */
    private static boolean addOpens(Module sourceModule, String packageName, Module targetModule) {
        try {
            Method implAddOpensMethod = Module.class.getDeclaredMethod(
                    "implAddOpens", String.class, Module.class);

            forceSetAccessible(implAddOpensMethod);
            implAddOpensMethod.invoke(sourceModule, packageName, targetModule);

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Force setAccessible bypass (Lombok style)
     */
    private static void forceSetAccessible(Object obj) throws Exception {
        try {

            if (obj instanceof Method) {
                ((Method) obj).setAccessible(true);
            } else if (obj instanceof Field) {
                ((Field) obj).setAccessible(true);
            }

        } catch (Exception e) {

            tryUnsafeSetAccessible(obj);
        }
    }

    /**
     * setAccessible bypass using Unsafe
     */
    private static void tryUnsafeSetAccessible(Object obj) throws Exception {
        try {

            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            Object unsafe = theUnsafeField.get(null);


            Class<?> accessibleObjectClass = Class.forName("java.lang.reflect.AccessibleObject");
            Field overrideField = accessibleObjectClass.getDeclaredField("override");

            Method objectFieldOffsetMethod = unsafeClass.getDeclaredMethod("objectFieldOffset", Field.class);
            long offset = (Long) objectFieldOffsetMethod.invoke(unsafe, overrideField);

            Method putBooleanMethod = unsafeClass.getDeclaredMethod("putBoolean", Object.class, long.class, boolean.class);
            putBooleanMethod.invoke(unsafe, obj, offset, true);

        } catch (Exception unsafeException) {
            throw new RuntimeException("All setAccessible bypass methods failed", unsafeException);
        }
    }
}
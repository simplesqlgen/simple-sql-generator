package io.github.simplesqlgen.annotation;

import io.github.simplesqlgen.enums.NamingStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface SqlGenerator {
    /**
     * 엔티티 타입. 엔티티 없이 NativeQuery만 사용할 경우 void.class로 둘 수 있습니다.
     */
    Class<?> entity() default void.class;
    String tableName() default "";
    boolean enableTransactions() default false;
    /**
     * 엔티티 기반 생성 기능 없이 @NativeQuery 메서드만 처리하도록 강제합니다.
     */
    boolean nativeQueryOnly() default false;
    NamingStrategy namingStrategy() default NamingStrategy.SNAKE_CASE;
}
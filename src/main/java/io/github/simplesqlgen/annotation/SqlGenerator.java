package io.github.simplesqlgen.annotation;

import io.github.simplesqlgen.enums.NamingStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface SqlGenerator {
    Class<?> entity() default void.class;
    String tableName() default "";
    boolean enableTransactions() default false;
    boolean nativeQueryOnly() default false;
    NamingStrategy namingStrategy() default NamingStrategy.SNAKE_CASE;
}
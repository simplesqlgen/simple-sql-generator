package io.github.simplesqlgen.annotation;

import io.github.simplesqlgen.enums.ParameterType;
import io.github.simplesqlgen.enums.ResultMappingType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface NativeQuery {
    String value();

    Class<?> resultType() default Object.class;
    ResultMappingType mappingType() default ResultMappingType.AUTO;
    String[] columnMapping() default {};
    boolean isUpdate() default false;
    ParameterType parameterType() default ParameterType.POSITIONAL;
    boolean validateSql() default true;
}
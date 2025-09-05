package io.github.simplesqlgen.enums;

public enum ResultMappingType {
    AUTO,           // 자동 매핑
    MANUAL,         // columnMapping 사용
    BEAN_PROPERTY,  // BeanPropertyRowMapper
    CONSTRUCTOR,    // 생성자 기반 매핑
    NESTED          // 중첩 DTO 매핑
}

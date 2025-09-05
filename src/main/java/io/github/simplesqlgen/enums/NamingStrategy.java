package io.github.simplesqlgen.enums;

/**
 * Naming strategy for table and column name generation.
 */
public enum NamingStrategy {
    SNAKE_CASE,   // userToken -> user_token
    CAMEL_CASE,   // userToken -> userToken
    PASCAL_CASE,  // userToken -> UserToken
    KEBAB_CASE,   // userToken -> user-token
    CUSTOM        // reserved for future user-defined strategy
}

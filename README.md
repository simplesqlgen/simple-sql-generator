# Simple SQL Generator

**Compile-time SQL generation with zero runtime overhead for Java applications.**

Simple SQL Generator is a Java annotation processor that automatically generates type-safe SQL execution code at compile time. Write clean, maintainable data access code without runtime reflection or complex ORM overhead.

## ✨ Why Simple SQL Generator?

- 🚀 **Zero Runtime Cost** - All SQL generated at compile time, no reflection overhead
- 🛡️ **Type Safe** - Compilation errors catch SQL parameter mismatches early
- 🗃️ **Multi-Database Support** - MySQL, PostgreSQL, Oracle, and more via dialect system
- ☕ **JDK 11-21+ Compatible** - Wide compatibility range for modern and legacy projects
- 📦 **Minimal Dependencies** - Only requires Google Auto Service for annotation processing
- 🔧 **IDE Friendly** - Full IntelliJ IDEA and Eclipse support with code completion

## 🚀 Quick Start

### Installation

Add Simple SQL Generator to your build tool:

**Maven:**
```xml
<dependency>
    <groupId>io.github.simplesqlgen</groupId>
    <artifactId>simple-sql-generator</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

**Gradle:**
```groovy
compileOnly 'io.github.simplesqlgen:simple-sql-generator:1.0.0'
annotationProcessor 'io.github.simplesqlgen:simple-sql-generator:1.0.0'
```

### Basic Usage

**1. Define your entity:**
```java
public class User {
    private Long id;
    private String name;
    private String email;
    private boolean active;
    
    // constructors, getters, setters...
}
```

**2. Create a repository class:**
```java
@Component
@SqlGenerator(entity = User.class, tableName = "users")
public class UserRepository {
    
    // These fields will be automatically injected by the processor
    // @Autowired private JdbcTemplate jdbcTemplate;
    // @Autowired private NamedParameterJdbcTemplate namedJdbcTemplate;
    
    // Native SQL queries
    @NativeQuery("SELECT * FROM users WHERE id = ?")
    public User findById(Long id) {
        // Implementation will be generated at compile time
        return null;
    }
    
    @NativeQuery("UPDATE users SET active = ? WHERE id = ?")
    public int updateActiveStatus(boolean active, Long id) {
        // Implementation will be generated at compile time
        return 0;
    }
    
    // Auto-generated queries from method names
    public List<User> findByName(String name) {
        // Implementation will be generated at compile time
        return null;
    }
    
    public List<User> findByEmailAndActive(String email, boolean active) {
        // Implementation will be generated at compile time
        return null;
    }
    
    public User findByEmail(String email) {
        // Implementation will be generated at compile time
        return null;
    }
    
    // CRUD operations
    public User save(User user) {
        // Implementation will be generated at compile time
        return null;
    }
    
    public int deleteById(Long id) {
        // Implementation will be generated at compile time
        return 0;
    }
    
    public boolean existsByEmail(String email) {
        // Implementation will be generated at compile time
        return false;
    }
    
    public long countByActive(boolean active) {
        // Implementation will be generated at compile time
        return 0;
    }
}
```

**3. Use in your application:**
```java
@Component
public class UserService {
    
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    public User createUser(String name, String email) {
        User user = new User(name, email, true);
        return userRepository.save(user);
    }
    
    public List<User> findActiveUsersByName(String name) {
        return userRepository.findByNameAndActive(name, true);
    }
}
```

## 📚 Features

### Native SQL Queries
Write raw SQL with type-safe parameter binding:

```java
@NativeQuery(value = "SELECT u.*, p.title as profile_title FROM users u LEFT JOIN profiles p ON u.id = p.user_id WHERE u.created_at > ?", 
            resultType = UserProfile.class)
public List<UserProfile> findUsersWithProfilesAfter(LocalDateTime date) {
    // Implementation generated at compile time
    return null;
}

// Named parameters
@NativeQuery("SELECT * FROM users WHERE name = :name AND age > :minAge")
public List<User> findByNameAndAgeGreaterThan(@Param("name") String name, @Param("minAge") int minAge) {
    // Implementation generated at compile time
    return null;
}
```

### Auto-Generated Queries
Method names are parsed to generate SQL automatically:

```java
// Generates: SELECT * FROM users WHERE name = ?
public List<User> findByName(String name) {
    return null; // Implementation generated at compile time
}

// Generates: SELECT * FROM users WHERE email = ? AND active = ?
public List<User> findByEmailAndActive(String email, boolean active) {
    return null; // Implementation generated at compile time
}

// Generates: SELECT COUNT(*) FROM users WHERE active = ?
public long countByActive(boolean active) {
    return 0; // Implementation generated at compile time
}

// Generates: DELETE FROM users WHERE id = ?
public int deleteById(Long id) {
    return 0; // Implementation generated at compile time
}

// Generates: SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)
public boolean existsByEmail(String email) {
    return false; // Implementation generated at compile time
}
```

### Result Mapping
Flexible result mapping strategies:

```java
@NativeQuery(value = "SELECT name, email FROM users", 
            mappingType = ResultMappingType.CONSTRUCTOR,
            resultType = UserSummary.class)
public List<UserSummary> getUserSummaries() {
    return null; // Implementation generated at compile time
}

@NativeQuery(value = "SELECT id, name, email, created_at FROM users",
            mappingType = ResultMappingType.FIELD_MAPPING,
            columnMapping = {"id", "name", "email", "createdAt"})
public List<User> getAllUsers() {
    return null; // Implementation generated at compile time
}
```

## 🗃️ Database Support

Simple SQL Generator supports multiple databases through a dialect system:

```java
@Component
@SqlGenerator(entity = User.class, dialect = MySQLDialect.class)
public class MySQLUserRepository { /* ... */ }

@Component
@SqlGenerator(entity = User.class, dialect = PostgreSQLDialect.class) 
public class PostgresUserRepository { /* ... */ }

@Component
@SqlGenerator(entity = User.class, dialect = OracleDialect.class)
public class OracleUserRepository { /* ... */ }
```

**Supported Databases:**
- MySQL 5.7+
- PostgreSQL 10+
- Oracle 12c+
- H2 Database
- SQL Server 2017+

## ⚙️ Configuration

### Naming Strategies
Control how entity names map to table/column names:

```java
@Component
@SqlGenerator(entity = User.class, 
              namingStrategy = NamingStrategy.SNAKE_CASE) // user_profile -> USER_PROFILE
public class UserRepository { }

@Component
@SqlGenerator(entity = User.class,
              namingStrategy = NamingStrategy.CAMEL_CASE) // userProfile -> userProfile  
public class UserRepository { }
```

### Native Query Only Mode
Use only @NativeQuery annotations without entity-based generation:

```java
@Component
@SqlGenerator(nativeQueryOnly = true)
public class CustomQueryRepository {
    @NativeQuery("SELECT custom_function(?) as result")
    public String executeCustomFunction(String input) {
        return null; // Implementation generated at compile time
    }
}
```

## 🔧 Integration

### Spring Boot
Simple SQL Generator integrates seamlessly with Spring Boot:

```java
@Repository
@SqlGenerator(entity = User.class)
public class UserRepository {
    // Repository methods with empty bodies that will be filled at compile time
    public List<User> findAll() {
        return null; // Implementation generated
    }
}

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository; // Auto-injected
}
```

### Testing
Write clean tests for your repositories:

```java
@Test
void testUserRepository() {
    User user = new User("John Doe", "john@example.com", true);
    User saved = userRepository.save(user);
    
    assertThat(saved.getId()).isNotNull();
    assertThat(userRepository.existsByEmail("john@example.com")).isTrue();
}
```

## 📖 Comparison

| Feature | Simple SQL Generator | MyBatis | JPA/Hibernate | JOOQ |
|---------|---------------------|---------|---------------|------|
| Compile-time generation | ✅ | ❌ | ❌ | ✅ |
| Zero runtime overhead | ✅ | ❌ | ❌ | ❌ |
| Type safety | ✅ | ⚠️ | ✅ | ✅ |
| Native SQL support | ✅ | ✅ | ⚠️ | ✅ |
| Learning curve | Low | Medium | High | High |
| Annotation-based | ✅ | ⚠️ | ✅ | ❌ |

## 🎯 Requirements

- **JDK**: 11, 17, 21+ (tested and verified)
- **Build Tools**: Maven 3.6+, Gradle 6.0+
- **Databases**: MySQL, PostgreSQL, Oracle, H2, SQL Server
- **IDE**: IntelliJ IDEA, Eclipse, VS Code

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Inspired by the simplicity of [Lombok](https://projectlombok.org/) and the power of [JOOQ](https://www.jooq.org/)
- Built on top of Google's [Auto Service](https://github.com/google/auto/tree/master/service) annotation processor framework
- Thanks to the Java community for annotation processing standards (JSR 269)

---

<p align="center">
  <strong>Made with ❤️ by the Simple SQL Generator team</strong><br>
  Star ⭐ this repo if you find it useful!
</p>
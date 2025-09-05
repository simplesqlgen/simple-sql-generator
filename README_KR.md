# Simple SQL Generator

**컴파일 타임에 SQL을 생성하여 런타임 오버헤드가 전혀 없는 Java 라이브러리입니다.**

Simple SQL Generator는 컴파일 타임에 타입 안전한 SQL 실행 코드를 자동으로 생성하는 Java 어노테이션 프로세서입니다. 복잡한 ORM이나 런타임 리플렉션 없이도 깔끔하고 유지보수가 쉬운 데이터 접근 코드를 작성할 수 있습니다.

## ✨ Simple SQL Generator를 선택해야 하는 이유

- 🚀 **런타임 비용 제로** - 모든 SQL이 컴파일 타임에 생성되어 리플렉션 오버헤드 없음
- 🛡️ **타입 안전성** - SQL 파라미터 불일치를 컴파일 시점에 미리 발견
- 🗃️ **다중 데이터베이스 지원** - 방언 시스템을 통해 MySQL, PostgreSQL, Oracle 등 지원
- ☕ **JDK 11-21+ 호환** - 최신 프로젝트부터 레거시 프로젝트까지 폭넓은 호환성
- 📦 **최소한의 의존성** - 어노테이션 처리를 위한 Google Auto Service만 필요
- 🔧 **IDE 친화적** - IntelliJ IDEA, Eclipse에서 코드 완성 기능 완벽 지원

## 🚀 빠른 시작

### 설치 방법

빌드 도구에 Simple SQL Generator를 추가하세요:

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

### 기본 사용법

**1단계: 엔티티 정의**
```java
public class User {
    private Long id;
    private String name;
    private String email;
    private boolean active;
    
    // 생성자, getter, setter...
}
```

**2단계: 레포지토리 클래스 생성**
```java
@Component
@SqlGenerator(entity = User.class, tableName = "users")
public class UserRepository {
    
    // 이 필드들은 프로세서에 의해 자동으로 주입됩니다
    // @Autowired private JdbcTemplate jdbcTemplate;
    // @Autowired private NamedParameterJdbcTemplate namedJdbcTemplate;
    
    // 직접 SQL 작성
    @NativeQuery("SELECT * FROM users WHERE id = ?")
    public User findById(Long id) {
        // 컴파일 타임에 구현이 생성됩니다
        return null;
    }
    
    @NativeQuery("UPDATE users SET active = ? WHERE id = ?")
    public int updateActiveStatus(boolean active, Long id) {
        // 컴파일 타임에 구현이 생성됩니다
        return 0;
    }
    
    // 메서드 이름으로 자동 SQL 생성
    public List<User> findByName(String name) {
        // 컴파일 타임에 구현이 생성됩니다
        return null;
    }
    
    public List<User> findByEmailAndActive(String email, boolean active) {
        // 컴파일 타임에 구현이 생성됩니다
        return null;
    }
    
    public User findByEmail(String email) {
        // 컴파일 타임에 구현이 생성됩니다
        return null;
    }
    
    // CRUD 연산
    public User save(User user) {
        // 컴파일 타임에 구현이 생성됩니다
        return null;
    }
    
    public int deleteById(Long id) {
        // 컴파일 타임에 구현이 생성됩니다
        return 0;
    }
    
    public boolean existsByEmail(String email) {
        // 컴파일 타임에 구현이 생성됩니다
        return false;
    }
    
    public long countByActive(boolean active) {
        // 컴파일 타임에 구현이 생성됩니다
        return 0;
    }
}
```

**3단계: 애플리케이션에서 사용**
```java
@Service
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

## 📚 주요 기능

### 직접 SQL 쿼리 작성
타입 안전한 파라미터 바인딩으로 원시 SQL 작성:

```java
@NativeQuery(value = "SELECT u.*, p.title as profile_title FROM users u LEFT JOIN profiles p ON u.id = p.user_id WHERE u.created_at > ?", 
            resultType = UserProfile.class)
public List<UserProfile> findUsersWithProfilesAfter(LocalDateTime date) {
    // 컴파일 타임에 구현이 생성됩니다
    return null;
}

// 이름 있는 파라미터 사용
@NativeQuery("SELECT * FROM users WHERE name = :name AND age > :minAge")
public List<User> findByNameAndAgeGreaterThan(@Param("name") String name, @Param("minAge") int minAge) {
    // 컴파일 타임에 구현이 생성됩니다
    return null;
}
```

### 메서드 이름 기반 자동 SQL 생성
메서드 이름을 분석하여 자동으로 SQL 생성:

```java
// 생성되는 SQL: SELECT * FROM users WHERE name = ?
public List<User> findByName(String name) {
    return null; // 컴파일 타임에 구현이 생성됩니다
}

// 생성되는 SQL: SELECT * FROM users WHERE email = ? AND active = ?
public List<User> findByEmailAndActive(String email, boolean active) {
    return null; // 컴파일 타임에 구현이 생성됩니다
}

// 생성되는 SQL: SELECT COUNT(*) FROM users WHERE active = ?
public long countByActive(boolean active) {
    return 0; // 컴파일 타임에 구현이 생성됩니다
}

// 생성되는 SQL: DELETE FROM users WHERE id = ?
public int deleteById(Long id) {
    return 0; // 컴파일 타임에 구현이 생성됩니다
}

// 생성되는 SQL: SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)
public boolean existsByEmail(String email) {
    return false; // 컴파일 타임에 구현이 생성됩니다
}
```

### 유연한 결과 매핑
다양한 결과 매핑 전략 지원:

```java
@NativeQuery(value = "SELECT name, email FROM users", 
            mappingType = ResultMappingType.CONSTRUCTOR,
            resultType = UserSummary.class)
public List<UserSummary> getUserSummaries() {
    return null; // 컴파일 타임에 구현이 생성됩니다
}

@NativeQuery(value = "SELECT id, name, email, created_at FROM users",
            mappingType = ResultMappingType.FIELD_MAPPING,
            columnMapping = {"id", "name", "email", "createdAt"})
public List<User> getAllUsers() {
    return null; // 컴파일 타임에 구현이 생성됩니다
}
```

## 🗃️ 데이터베이스 지원

방언 시스템을 통한 다중 데이터베이스 지원:

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

**지원하는 데이터베이스:**
- MySQL 5.7+
- PostgreSQL 10+
- Oracle 12c+
- H2 Database
- SQL Server 2017+

## ⚙️ 설정

### 네이밍 전략
엔티티 이름을 테이블/컬럼 이름으로 매핑하는 방식 제어:

```java
@Component
@SqlGenerator(entity = User.class, 
              namingStrategy = NamingStrategy.SNAKE_CASE) // userProfile -> user_profile
public class UserRepository { }

@Component
@SqlGenerator(entity = User.class,
              namingStrategy = NamingStrategy.CAMEL_CASE) // userProfile -> userProfile  
public class UserRepository { }
```

### 네이티브 쿼리 전용 모드
엔티티 기반 생성 없이 @NativeQuery 어노테이션만 사용:

```java
@Component
@SqlGenerator(nativeQueryOnly = true)
public class CustomQueryRepository {
    @NativeQuery("SELECT custom_function(?) as result")
    public String executeCustomFunction(String input) {
        return null; // 컴파일 타임에 구현이 생성됩니다
    }
}
```

## 🔧 프레임워크 통합

### Spring Boot 연동
Spring Boot와 완벽하게 통합:

```java
@Repository
@SqlGenerator(entity = User.class)
public class UserRepository {
    // 컴파일 타임에 채워질 빈 본문을 가진 레포지토리 메서드들
    public List<User> findAll() {
        return null; // 구현이 생성됩니다
    }
}

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository; // 자동 주입
}
```

### 테스트 작성
레포지토리를 위한 깔끔한 테스트 코드:

```java
@Test
void testUserRepository() {
    User user = new User("홍길동", "hong@example.com", true);
    User saved = userRepository.save(user);
    
    assertThat(saved.getId()).isNotNull();
    assertThat(userRepository.existsByEmail("hong@example.com")).isTrue();
}
```

## 📖 기존 솔루션과 비교

| 기능 | Simple SQL Generator | MyBatis | JPA/Hibernate | JOOQ |
|---------|---------------------|---------|---------------|------|
| 컴파일 타임 생성 | ✅ | ❌ | ❌ | ✅ |
| 런타임 오버헤드 제로 | ✅ | ❌ | ❌ | ❌ |
| 타입 안전성 | ✅ | ⚠️ | ✅ | ✅ |
| 네이티브 SQL 지원 | ✅ | ✅ | ⚠️ | ✅ |
| 학습 곡선 | 낮음 | 보통 | 높음 | 높음 |
| 어노테이션 기반 | ✅ | ⚠️ | ✅ | ❌ |

## 🎯 시스템 요구사항

- **JDK**: 11, 17, 21+ (테스트 완료)
- **빌드 도구**: Maven 3.6+, Gradle 6.0+
- **데이터베이스**: MySQL, PostgreSQL, Oracle, H2, SQL Server
- **IDE**: IntelliJ IDEA, Eclipse, VS Code

## 🏢 실무 활용 사례

### 전자상거래 시스템
```java
@Component
@SqlGenerator(entity = Order.class)
public class OrderRepository {
    // 주문 상태별 조회
    public List<Order> findByStatusAndUserId(OrderStatus status, Long userId) {
        return null; // 컴파일 타임에 구현이 생성됩니다
    }
    
    // 기간별 매출 집계
    @NativeQuery("SELECT DATE(created_at) as date, SUM(total_amount) as sales " +
                "FROM orders WHERE created_at BETWEEN ? AND ? GROUP BY DATE(created_at)")
    public List<DailySales> getDailySalesBetween(LocalDate start, LocalDate end) {
        return null; // 컴파일 타임에 구현이 생성됩니다
    }
}
```

### 사용자 관리 시스템
```java
@Component
@SqlGenerator(entity = Member.class, tableName = "members")
public class MemberRepository {
    // 활성 회원 조회
    public List<Member> findByActiveAndGrade(boolean active, MemberGrade grade) {
        return null; // 컴파일 타임에 구현이 생성됩니다
    }
    
    // 복잡한 검색 조건
    @NativeQuery("SELECT * FROM members m " +
                "WHERE (:name IS NULL OR m.name LIKE CONCAT('%', :name, '%')) " +
                "AND (:email IS NULL OR m.email = :email)")
    public List<Member> searchMembers(@Param("name") String name, @Param("email") String email) {
        return null; // 컴파일 타임에 구현이 생성됩니다
    }
}
```

## 🛠️ IntelliJ IDEA 설정 팁

### 어노테이션 프로세싱 활성화
```
File → Settings → Build → Compiler → Annotation Processors
☑️ Enable annotation processing
```

### 자동 임포트 설정
```java
// 자주 사용하는 임포트를 자동으로 추가
import com.simplesqlgen.annotation.SqlGenerator;
import com.simplesqlgen.annotation.NativeQuery;
import com.simplesqlgen.annotation.Param;
```

## 🤝 기여하기

기여를 환영합니다! 자세한 내용은 [기여 가이드](CONTRIBUTING.md)를 참조하세요.

1. **Fork** 저장소를 포크하세요
2. **브랜치 생성** (`git checkout -b feature/awesome-feature`)
3. **변경사항 커밋** (`git commit -m 'Add awesome feature'`)
4. **브랜치에 푸시** (`git push origin feature/awesome-feature`)
5. **Pull Request 생성**

## 📄 라이센스

이 프로젝트는 Apache License 2.0 하에 배포됩니다 - 자세한 내용은 [LICENSE](LICENSE) 파일을 참조하세요.

## 🙏 감사의 말

- [Lombok](https://projectlombok.org/)의 단순함과 [JOOQ](https://www.jooq.org/)의 강력함에서 영감을 받았습니다
- Google의 [Auto Service](https://github.com/google/auto/tree/master/service) 어노테이션 프로세서 프레임워크 기반으로 구축
- 어노테이션 처리 표준(JSR 269)을 위한 Java 커뮤니티에 감사드립니다

---

<p align="center">
  <strong>Simple SQL Generator 팀이 ❤️로 만들었습니다</strong><br>
  이 프로젝트가 도움이 되셨다면 ⭐ 스타를 눌러주세요!
</p>
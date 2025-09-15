package io.github.simplesqlgen.examples;

import io.github.simplesqlgen.annotation.SqlGenerator;
import io.github.simplesqlgen.annotation.NativeQuery;
import java.util.List;

// This is a test class to see actual code generation
@SqlGenerator(entity = User.class, tableName = "users")
public class TestRepository {
    
    // This method should get @Autowired fields added and body replaced
    public List<User> findByName(String name) {
        return null;
    }
    
    public long countByName(String name) {
        return 0;
    }
    
    @NativeQuery(value = "SELECT * FROM users WHERE email = ?", resultType = User.class)
    public List<User> findByEmail(String email) {
        return null;
    }
}
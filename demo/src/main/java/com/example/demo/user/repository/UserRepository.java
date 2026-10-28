package com.example.demo.user.repository;
import com.example.demo.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from User u where u.id=:id") Optional<User> findForUpdate(Long id);
    @Query(value="SELECT EXISTS(SELECT 1 FROM users WHERE username_key=LOWER(:value) AND id<>:id)",nativeQuery=true) boolean usernameExists(String value,Long id);
    @Query(value="SELECT EXISTS(SELECT 1 FROM users WHERE email_key=LOWER(:value) AND id<>:id)",nativeQuery=true) boolean emailExists(String value,Long id);
    @Query(value="SELECT LOWER(CAST(:value AS TEXT))",nativeQuery=true) String lowercase(String value);
    @Query(value="SELECT set_config('lock_timeout', :timeout, TRUE)",nativeQuery=true) String setLockTimeout(String timeout);
    @Query(value="SELECT * FROM users WHERE username_key=LOWER(:identifier)",nativeQuery=true)
    Optional<User> findByUsernameIdentifier(String identifier);
    @Query(value="SELECT * FROM users WHERE email_key=LOWER(:identifier)",nativeQuery=true)
    Optional<User> findByEmailIdentifier(String identifier);
    @org.springframework.data.jpa.repository.Modifying
    @Query(value="INSERT INTO user_roles(user_id,role_id) SELECT :id,id FROM roles WHERE name='USER' ON CONFLICT DO NOTHING",nativeQuery=true)
    void assignDefaultRole(Long id);
    @Query(value="SELECT 'ROLE_' || r.name FROM roles r JOIN user_roles ur ON ur.role_id=r.id WHERE ur.user_id=:id UNION SELECT p.name FROM permissions p JOIN role_permissions rp ON rp.permission_id=p.id JOIN user_roles ur ON ur.role_id=rp.role_id WHERE ur.user_id=:id",nativeQuery=true)
    java.util.List<String> findAuthorities(Long id);
}

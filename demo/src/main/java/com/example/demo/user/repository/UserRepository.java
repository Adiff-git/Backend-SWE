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
}

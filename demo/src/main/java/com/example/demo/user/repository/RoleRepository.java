package com.example.demo.user.repository;
import com.example.demo.user.entity.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RoleRepository extends JpaRepository<Role,Long> { Optional<Role> findByName(String name); }

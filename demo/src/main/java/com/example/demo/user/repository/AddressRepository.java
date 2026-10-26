package com.example.demo.user.repository;
import com.example.demo.user.entity.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AddressRepository extends JpaRepository<Address,Long> {
    List<Address> findByUserIdOrderByIsDefaultDescIdDesc(Long userId);
    Optional<Address> findByIdAndUserId(Long id,Long userId);
}

package com.example.demo.user.service.impl;
import com.example.demo.user.service.UserService;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.converter.UserConverter;
import com.example.demo.user.validator.UserValidator;
import com.example.demo.user.dto.request.UpdateUserRequest;
import com.example.demo.user.dto.response.PrivateUserResponse;
import com.example.demo.user.dto.response.PublicUserResponse;
import com.example.demo.user.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.Transactional;
@Service @Validated @Transactional(readOnly=true)
public class UserServiceImpl implements UserService {
    private final UserRepository repository;
    private final UserConverter converter;
    private final UserValidator validator;
    private final EntityManager entityManager;
    private final long lockTimeoutMs;
    public UserServiceImpl(UserRepository repository,UserConverter converter,UserValidator validator,EntityManager entityManager,@Value("${user.lock-timeout-ms:5000}") long lockTimeoutMs) {
        if(lockTimeoutMs<=0) throw new IllegalArgumentException("Lock timeout must be positive");
        this.repository=repository;
        this.converter=converter;
        this.validator=validator;
        this.entityManager=entityManager;
        this.lockTimeoutMs=lockTimeoutMs;
    }
    public User getUserEntity(Long id) {
        requireActor(id);
        return repository.findById(id).orElseThrow(ResourceNotFoundException::new);
    }
    @Transactional public User getUserEntityForUpdate(Long id) {
        requireActor(id);
        repository.setLockTimeout(lockTimeoutMs+"ms");
        User user=repository.findForUpdate(id).orElseThrow(ResourceNotFoundException::new);
        entityManager.refresh(user,LockModeType.PESSIMISTIC_WRITE);
        return user;
    }
    public PrivateUserResponse getCurrentUser(Long actorId) {
        return converter.toPrivate(getUserEntity(actorId));
    }
    public PublicUserResponse getUser(Long id) {
        return converter.toPublic(getUserEntity(id));
    }
    @Transactional public PrivateUserResponse updateUser(Long actorId,UpdateUserRequest request) {
        // Transaction is established on this public entry point; self-call does not establish it.
        User user=getUserEntityForUpdate(actorId);
        validator.validate(request,request.getUsername()!=null && repository.usernameExists(request.getUsername(),actorId),request.getEmail()!=null && repository.emailExists(request.getEmail(),actorId));
        if(request.getUsername()!=null) user.setUsername(request.getUsername());
        if(request.getEmail()!=null) user.setEmail(repository.lowercase(request.getEmail()));
        if(request.getDisplayName()!=null) user.setDisplayName(request.getDisplayName());
        if(request.getPhoneNumber()!=null) user.setPhoneNumber(request.getPhoneNumber());
        if(request.getGender()!=null) user.setGender(request.getGender());
        if(request.getDateOfBirth()!=null) user.setDateOfBirth(request.getDateOfBirth());
        repository.flush();
        return converter.toPrivate(user);
    }
    private static void requireActor(Long id) {
        if(id==null) throw new com.example.demo.common.identity.IdentityUnavailableException();
    }
    public java.util.Optional<Long> resolveSecurityIdentifier(String identifier) {
        if(identifier==null || identifier.strip().isEmpty())return java.util.Optional.empty();
        String normalized=identifier.strip();
        return (normalized.contains("@")?repository.findByEmailIdentifier(normalized):repository.findByUsernameIdentifier(normalized)).map(User::getId);
    }
    public com.example.demo.user.dto.response.UserSecuritySnapshot getUserSecuritySnapshot(Long id) {
        User user=getUserEntity(id);
        return new com.example.demo.user.dto.response.UserSecuritySnapshot(user.getId(),user.getUsername(),user.getPassword(),user.getAccountStatus(),java.util.Set.copyOf(repository.findAuthorities(id)));
    }
    @Transactional public Long createAuthenticationUser(String username,String encodedPassword) {
        String normalized=username==null?null:username.strip();
        if(normalized==null || normalized.isBlank() || normalized.contains("@") || normalized.codePointCount(0,normalized.length())>50)
        throw new com.example.demo.user.exception.BusinessValidationException("Invalid username");
        if(encodedPassword==null || !encodedPassword.matches("[$]2[aby][$]10[$][./A-Za-z0-9]{53}"))
        throw new com.example.demo.user.exception.BusinessValidationException("Encoded password required");
        User user=new User();
        user.setUsername(normalized);
        user.setPassword(encodedPassword);
        user.setAccountStatus(com.example.demo.user.enums.AccountStatus.ACTIVE);
        repository.saveAndFlush(user);
        repository.assignDefaultRole(user.getId());
        if(!repository.findAuthorities(user.getId()).contains("ROLE_USER"))throw new IllegalStateException("Default role is unavailable");
        return user.getId();
    }
    @Transactional public void replaceEncodedPassword(Long id,String encodedPassword) {
        if(encodedPassword==null || !encodedPassword.matches("[$]2[aby][$]10[$][./A-Za-z0-9]{53}"))
        throw new com.example.demo.user.exception.BusinessValidationException("Encoded password required");
        User user=getUserEntityForUpdate(id);
        user.setPassword(encodedPassword);
        repository.flush();
    }
}

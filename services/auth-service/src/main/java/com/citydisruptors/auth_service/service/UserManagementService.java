package com.citydisruptors.auth_service.service;

import com.citydisruptors.auth_service.entity.Role;
import com.citydisruptors.auth_service.entity.User;
import com.citydisruptors.auth_service.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final MeterRegistry registry;
    private final SecurityAuditLogger securityAudit;

    public UserManagementService(
            UserRepository userRepository,
            MeterRegistry registry,
            SecurityAuditLogger securityAudit
    ) {
        this.userRepository = userRepository;
        this.registry = registry;
        this.securityAudit = securityAudit;
    }

    public List<User> getUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User updateRole(String email, Role role, String changedBy) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

        Role previousRole = user.getRole();
        user.setRole(role);

        registry.counter(
                "auth.users.role.updated",
                "role", role.name()
        ).increment();

        User saved = userRepository.save(user);
        securityAudit.roleChanged(saved, previousRole, changedBy);
        return saved;
    }
}
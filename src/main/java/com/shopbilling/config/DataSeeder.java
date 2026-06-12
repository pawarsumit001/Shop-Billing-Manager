package com.shopbilling.config;

import com.shopbilling.model.AppUser;
import com.shopbilling.model.UserRole;
import com.shopbilling.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (users.count() == 0) {
            AppUser owner = new AppUser();
            owner.setName("Owner");
            owner.setUsername("owner");
            owner.setPasswordHash(passwordEncoder.encode("owner123"));
            owner.setRole(UserRole.OWNER);
            users.save(owner);

            AppUser staff = new AppUser();
            staff.setName("Staff");
            staff.setUsername("staff");
            staff.setPasswordHash(passwordEncoder.encode("staff123"));
            staff.setRole(UserRole.STAFF);
            users.save(staff);
        }
    }
}

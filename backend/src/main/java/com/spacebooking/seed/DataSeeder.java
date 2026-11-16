package com.spacebooking.seed;

import com.spacebooking.config.SeedProperties;
import com.spacebooking.room.domain.Room;
import com.spacebooking.room.infra.RoomRepository;
import com.spacebooking.user.domain.Role;
import com.spacebooking.user.domain.User;
import com.spacebooking.user.infra.UserRepository;
import java.time.Clock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Seed data per domain.md section 13. Idempotent: existing emails and room names are skipped. */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final RoomRepository rooms;
    private final PasswordEncoder encoder;
    private final SeedProperties props;
    private final Clock clock;

    public DataSeeder(UserRepository users, RoomRepository rooms, PasswordEncoder encoder, SeedProperties props,
                      Clock clock) {
        this.users = users;
        this.rooms = rooms;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    public void seed() {
        seedUser("admin@spacebooking.local", props.adminPassword(), "Quản trị viên", Role.ADMIN);
        seedUser("manager@spacebooking.local", props.managerPassword(), "Lê Minh Quân", Role.MANAGER);
        seedUser("employee@spacebooking.local", props.employeePassword(), "Trần Thị Bình", Role.EMPLOYEE);

        seedRoom("Phòng Sen", "Tầng 3", 6, "tv,whiteboard", false);
        seedRoom("Phòng Mai", "Tầng 3", 4, "whiteboard", false);
        seedRoom("Phòng Cúc", "Tầng 2", 2, "tvbox", false);
        seedRoom("Phòng Trường Sơn", "Tầng 5", 12, "tv,video-conference,whiteboard", true);
        seedRoom("Hội trường Bạch Đằng", "Tầng 1", 40, "microphone,projector,speaker", true);
    }

    private void seedUser(String email, String password, String fullName, Role role) {
        if (users.existsByEmail(email)) {
            return;
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Seeding is enabled but the password for " + email + " is not set");
        }
        users.save(new User(email, encoder.encode(password), fullName, role, clock));
    }

    private void seedRoom(String name, String location, int capacity, String equipments, boolean requiresApproval) {
        if (!rooms.existsByName(name)) {
            rooms.save(new Room(name, location, capacity, equipments, requiresApproval));
        }
    }
}

package com.employeetrack.config;

import com.employeetrack.entity.Department;
import com.employeetrack.entity.Employee;
import com.employeetrack.entity.User;
import com.employeetrack.repository.DepartmentRepository;
import com.employeetrack.repository.EmployeeRepository;
import com.employeetrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Seeds the database with default Admin, HR accounts and sample employees
 * on first startup. Runs every startup but is IDEMPOTENT (checks before creating).
 *
 * Credentials are configurable via environment variables set in Render dashboard:
 *   ADMIN_EMAIL      (default: admin@employeetrack.com)
 *   ADMIN_PASSWORD   (default: Admin@123)
 *   HR_EMAIL         (default: hr@employeetrack.com)
 *   HR_PASSWORD      (default: HR@123)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    // Admin credentials — override via Render env vars for security
    @Value("${ADMIN_EMAIL:admin@employeetrack.com}")
    private String adminEmail;

    @Value("${ADMIN_PASSWORD:Admin@123}")
    private String adminPassword;

    // HR credentials — override via Render env vars for security
    @Value("${HR_EMAIL:hr@employeetrack.com}")
    private String hrEmail;

    @Value("${HR_PASSWORD:HR@123}")
    private String hrPassword;

    @Override
    public void run(String... args) {
        initDepartments();
        initUsers();
        log.info("Data initialization complete.");
    }

    private void initDepartments() {
        if (departmentRepository.count() == 0) {
            List<Department> departments = List.of(
                Department.builder().name("Engineering").description("Software and hardware development").build(),
                Department.builder().name("Human Resources").description("HR and talent management").build(),
                Department.builder().name("Finance").description("Finance and accounting").build(),
                Department.builder().name("Marketing").description("Marketing and communications").build(),
                Department.builder().name("Operations").description("Operations and administration").build(),
                Department.builder().name("Sales").description("Sales and business development").build()
            );
            departmentRepository.saveAll(departments);
            log.info("Departments initialized.");
        }
    }

    private void initUsers() {
        // Admin
        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = User.builder()
                .name("System Administrator")
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(User.Role.ADMIN)
                .enabled(true)
                .build();
            userRepository.save(admin);
            log.info("✅ Admin user created: {}", adminEmail);
        } else {
            // Sync password: if env var changed (or was empty on first deploy), re-hash.
            // This prevents "Invalid email or password" when the stored hash is stale/wrong.
            syncPasswordIfChanged(adminEmail, adminPassword, "Admin");
        }

        // HR
        if (!userRepository.existsByEmail(hrEmail)) {
            User hr = User.builder()
                .name("HR Manager")
                .email(hrEmail)
                .password(passwordEncoder.encode(hrPassword))
                .role(User.Role.HR)
                .enabled(true)
                .build();
            userRepository.save(hr);
            log.info("✅ HR user created: {}", hrEmail);
        } else {
            // Sync password: same reason as above.
            syncPasswordIfChanged(hrEmail, hrPassword, "HR");
        }

        // Sample Employee
        if (!userRepository.existsByEmail("employee@employeetrack.com")) {
            User empUser = User.builder()
                .name("John Smith")
                .email("employee@employeetrack.com")
                .password(passwordEncoder.encode("Employee@123"))
                .role(User.Role.EMPLOYEE)
                .enabled(true)
                .build();
            empUser = userRepository.save(empUser);

            Department dept = departmentRepository.findByName("Engineering").orElse(null);
            Employee employee = Employee.builder()
                .employeeId("EMP001")
                .user(empUser)
                .department(dept)
                .designation("Software Engineer")
                .phone("9876543210")
                .address("123 Main Street, Chennai, Tamil Nadu")
                .dateOfJoining(LocalDate.of(2023, 1, 15))
                .basicSalary(50000.0)
                .allowances(10000.0)
                .deductions(5000.0)
                .active(true)
                .build();
            employeeRepository.save(employee);
            log.info("Sample employee created: EMP001 / employee@employeetrack.com");
        }

        // More sample employees
        createSampleEmployee("jane.doe@employeetrack.com", "Jane Doe", "EMP002",
            "Human Resources", "HR Executive", "9876543211", 45000.0, 8000.0, 4000.0);
        createSampleEmployee("robert.brown@employeetrack.com", "Robert Brown", "EMP003",
            "Finance", "Financial Analyst", "9876543212", 55000.0, 12000.0, 6000.0);
        createSampleEmployee("emily.davis@employeetrack.com", "Emily Davis", "EMP004",
            "Marketing", "Marketing Manager", "9876543213", 60000.0, 15000.0, 7000.0);
    }

    private void createSampleEmployee(String email, String name, String empId,
            String deptName, String designation, String phone,
            double basic, double allowances, double deductions) {
        if (!userRepository.existsByEmail(email)) {
            User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("Employee@123"))
                .role(User.Role.EMPLOYEE)
                .enabled(true)
                .build();
            user = userRepository.save(user);

            Department dept = departmentRepository.findByName(deptName).orElse(null);
            Employee emp = Employee.builder()
                .employeeId(empId)
                .user(user)
                .department(dept)
                .designation(designation)
                .phone(phone)
                .address("Sample Address, Chennai, Tamil Nadu")
                .dateOfJoining(LocalDate.of(2023, 6, 1))
                .basicSalary(basic)
                .allowances(allowances)
                .deductions(deductions)
                .active(true)
                .build();
            employeeRepository.save(emp);
            log.info("Sample employee created: {} / {}", empId, email);
        }
    }

    /**
     * Compares the current configured plain-text password against the BCrypt hash
     * stored in the DB. If they don't match (e.g. env var was empty/wrong on first
     * deploy and has since been fixed), updates the hash so the correct password works.
     *
     * This is idempotent: if the hash already matches, nothing is written.
     */
    private void syncPasswordIfChanged(String email, String configuredPassword, String label) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!passwordEncoder.matches(configuredPassword, user.getPassword())) {
                user.setPassword(passwordEncoder.encode(configuredPassword));
                userRepository.save(user);
                log.warn("⚠️  {} password hash was stale/wrong — updated to match current env var. " +
                         "Login will now work with the configured password.", label);
            } else {
                log.info("{} password hash is current — no update needed.", label);
            }
        });
    }
}

package com.employeetrack.service;

import com.employeetrack.dto.LoginRequest;
import com.employeetrack.dto.LoginResponse;
import com.employeetrack.dto.RegisterRequest;
import com.employeetrack.entity.Department;
import com.employeetrack.entity.Employee;
import com.employeetrack.entity.User;
import com.employeetrack.repository.DepartmentRepository;
import com.employeetrack.repository.EmployeeRepository;
import com.employeetrack.repository.UserRepository;
import com.employeetrack.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        // ---- Password rules ----
        if (request.getPassword().length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters");
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Passwords do not match");
        }

        // ---- Duplicate email check ----
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("This email address is already registered. Please use a different email or sign in.");
        }

        // ---- Phone format: 10 digits, starts with 6/7/8/9 ----
        String phone = request.getPhone().trim();
        if (!phone.matches("^[6-9]\\d{9}$")) {
            throw new RuntimeException(
                "Enter a valid 10-digit Indian mobile number starting with 6, 7, 8, or 9");
        }

        // ---- Duplicate phone check ----
        if (employeeRepository.existsByPhone(phone)) {
            throw new RuntimeException("This phone number is already associated with another account.");
        }

        // ---- Name cannot be blank/whitespace ----
        if (request.getName().trim().isEmpty()) {
            throw new RuntimeException("Full name must not be blank");
        }

        // ---- Designation cannot be blank/whitespace ----
        if (request.getDesignation().trim().isEmpty()) {
            throw new RuntimeException("Designation must not be blank");
        }

        User user = User.builder()
            .name(request.getName().trim())
            .email(request.getEmail().trim().toLowerCase())
            .password(passwordEncoder.encode(request.getPassword()))
            .role(User.Role.EMPLOYEE)
            .enabled(true)
            .build();
        user = userRepository.save(user);

        // Find or create department
        Department dept = departmentRepository.findByName(request.getDepartment())
            .orElseGet(() -> departmentRepository.save(
                Department.builder().name(request.getDepartment()).build()));

        // Generate employee ID
        long count = employeeRepository.count() + 1;
        String empId = String.format("EMP%03d", count);
        while (employeeRepository.existsByEmployeeId(empId)) {
            count++;
            empId = String.format("EMP%03d", count);
        }

        Employee employee = Employee.builder()
            .employeeId(empId)
            .user(user)
            .department(dept)
            .designation(request.getDesignation().trim())
            .phone(phone)
            .dateOfJoining(LocalDate.now())
            .basicSalary(0.0)
            .allowances(0.0)
            .deductions(0.0)
            .active(true)
            .build();
        employee = employeeRepository.save(employee);

        String token = jwtService.generateToken(user);

        return LoginResponse.builder()
            .token(token)
            .email(user.getEmail())
            .name(user.getName())
            .role(user.getRole().name())
            .employeeId(employee.getId())
            .message("Registration successful")
            .build();
    }

    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (AuthenticationException e) {
            throw new RuntimeException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("User not found"));

        Long employeeId = null;
        // Fetch employeeId for all roles — Admin/HR may also have an Employee record
        Optional<Employee> emp = employeeRepository.findByUser(user);
        if (emp.isPresent()) employeeId = emp.get().getId();

        String token = jwtService.generateToken(user);

        return LoginResponse.builder()
            .token(token)
            .email(user.getEmail())
            .name(user.getName())
            .role(user.getRole().name())
            .employeeId(employeeId)
            .message("Login successful")
            .build();
    }
}

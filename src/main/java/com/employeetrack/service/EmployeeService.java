package com.employeetrack.service;

import com.employeetrack.dto.EmployeeDTO;
import com.employeetrack.entity.Department;
import com.employeetrack.entity.Employee;
import com.employeetrack.entity.User;
import com.employeetrack.repository.DepartmentRepository;
import com.employeetrack.repository.EmployeeRepository;
import com.employeetrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    public List<EmployeeDTO> getAllEmployees() {
        return employeeRepository.findAll().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    public EmployeeDTO getEmployeeById(Long id) {
        Employee emp = employeeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Employee not found"));
        return toDTO(emp);
    }

    public EmployeeDTO getMyProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        Employee emp = employeeRepository.findByUser(user)
            .orElseThrow(() -> new RuntimeException("Employee profile not found"));
        return toDTO(emp);
    }

    @Transactional
    public EmployeeDTO updateEmployee(Long id, EmployeeDTO dto) {
        Employee emp = employeeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(dto.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"));
            emp.setDepartment(dept);
        }
        if (dto.getDesignation() != null) emp.setDesignation(dto.getDesignation());
        if (dto.getPhone() != null) emp.setPhone(dto.getPhone());
        if (dto.getAddress() != null) emp.setAddress(dto.getAddress());
        if (dto.getDateOfJoining() != null) emp.setDateOfJoining(dto.getDateOfJoining());

        // ── Bank details validation ──────────────────────────────────────────
        if (dto.getBankName() != null) {
            String bankName = dto.getBankName().trim();
            if (bankName.isEmpty()) throw new RuntimeException("Bank name must not be empty.");
            if (bankName.length() < 2 || bankName.length() > 100)
                throw new RuntimeException("Bank name must be between 2 and 100 characters.");
            emp.setBankName(bankName);
        }
        if (dto.getAccountNumber() != null) {
            String acc = dto.getAccountNumber().trim();
            if (!acc.isEmpty()) {
                if (!acc.matches("\\d{9,18}"))
                    throw new RuntimeException(
                        "Account number must contain only digits and be between 9 and 18 digits long.");
            }
            emp.setAccountNumber(acc.isEmpty() ? null : acc);
        }
        if (dto.getIfscCode() != null) {
            String ifsc = dto.getIfscCode().trim().toUpperCase();
            if (!ifsc.isEmpty()) {
                // Standard IFSC: 4 letters + "0" + 6 alphanumeric characters
                if (!ifsc.matches("^[A-Z]{4}0[A-Z0-9]{6}$"))
                    throw new RuntimeException(
                        "IFSC code must follow the format: 4 letters, then 0, then 6 alphanumeric characters (e.g. SBIN0001234).");
            }
            emp.setIfscCode(ifsc.isEmpty() ? null : ifsc);
        }
        if (dto.getPanNumber() != null) {
            String pan = dto.getPanNumber().trim().toUpperCase();
            if (!pan.isEmpty()) {
                // Standard Indian PAN: 5 letters + 4 digits + 1 letter
                if (!pan.matches("^[A-Z]{5}[0-9]{4}[A-Z]$"))
                    throw new RuntimeException(
                        "PAN number must follow the format: 5 letters, 4 digits, 1 letter (e.g. ABCDE1234F).");
            }
            emp.setPanNumber(pan.isEmpty() ? null : pan);
        }
        // ── End bank details validation ──────────────────────────────────────

        if (dto.getBasicSalary() != null && dto.getBasicSalary() >= 0) emp.setBasicSalary(dto.getBasicSalary());
        if (dto.getAllowances() != null && dto.getAllowances() >= 0) emp.setAllowances(dto.getAllowances());
        if (dto.getDeductions() != null && dto.getDeductions() >= 0) emp.setDeductions(dto.getDeductions());

        return toDTO(employeeRepository.save(emp));
    }

    @Transactional
    public EmployeeDTO updateMyProfile(EmployeeDTO dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        Employee emp = employeeRepository.findByUser(user)
            .orElseThrow(() -> new RuntimeException("Employee profile not found"));
        return updateEmployee(emp.getId(), dto);
    }

    /**
     * Dedicated salary configuration method for Admin.
     * Validates that salary values are valid before saving.
     */
    @Transactional
    public EmployeeDTO updateEmployeeSalary(Long id, Double basicSalary, Double allowances, Double deductions) {
        Employee emp = employeeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (basicSalary == null || basicSalary < 0) {
            throw new RuntimeException("Basic salary must be a positive value.");
        }
        if (allowances == null || allowances < 0) {
            throw new RuntimeException("Allowances must be zero or a positive value.");
        }
        if (deductions == null || deductions < 0) {
            throw new RuntimeException("Deductions must be zero or a positive value.");
        }
        double gross = basicSalary + allowances;
        if (deductions > gross) {
            throw new RuntimeException("Deductions (" + deductions + ") cannot exceed gross salary (" + gross + ").");
        }

        emp.setBasicSalary(basicSalary);
        emp.setAllowances(allowances);
        emp.setDeductions(deductions);
        return toDTO(employeeRepository.save(emp));
    }

    @Transactional
    public void toggleEmployeeStatus(Long id) {
        Employee emp = employeeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Employee not found"));
        emp.setActive(!emp.isActive());
        employeeRepository.save(emp);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new RuntimeException("Employee not found");
        }
        employeeRepository.deleteById(id);
    }

    /**
     * Resets the password for the user account linked to this employee.
     * Rules enforced:
     *  - newPassword must be at least 8 characters.
     *  - newPassword and confirmPassword must match.
     *  - Password is BCrypt-hashed before being saved to the database.
     *  - Plain-text password is never stored, logged, or returned.
     */
    @Transactional
    public void resetPassword(Long employeeId, String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new RuntimeException("New password must not be empty.");
        }
        if (newPassword.length() < 8) {
            throw new RuntimeException("Password must be at least 8 characters long.");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new RuntimeException("Passwords do not match.");
        }

        Employee emp = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new RuntimeException("Employee not found."));

        User user = emp.getUser();
        // BCrypt-hash the new password — plain text is never persisted
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        // newPassword reference goes out of scope here; not returned or logged
    }
    public EmployeeDTO toDTO(Employee emp) {
        return EmployeeDTO.builder()
            .id(emp.getId())
            .employeeId(emp.getEmployeeId())
            .name(emp.getUser().getName())
            .email(emp.getUser().getEmail())
            .phone(emp.getPhone())
            .address(emp.getAddress())
            .designation(emp.getDesignation())
            .departmentId(emp.getDepartment() != null ? emp.getDepartment().getId() : null)
            .departmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : null)
            .dateOfJoining(emp.getDateOfJoining())
            .bankName(emp.getBankName())
            .accountNumber(emp.getAccountNumber())
            .ifscCode(emp.getIfscCode())
            .panNumber(emp.getPanNumber())
            .basicSalary(emp.getBasicSalary())
            .allowances(emp.getAllowances())
            .deductions(emp.getDeductions())
            .active(emp.isActive())
            .build();
    }
}

package com.employeetrack.controller;

import com.employeetrack.dto.EmployeeDTO;
import com.employeetrack.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<List<EmployeeDTO>> getAllEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployees());
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<EmployeeDTO> getMyProfile() {
        return ResponseEntity.ok(employeeService.getMyProfile());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<EmployeeDTO> updateMyProfile(@RequestBody EmployeeDTO dto) {
        return ResponseEntity.ok(employeeService.updateMyProfile(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<EmployeeDTO> getEmployeeById(@PathVariable Long id) {
        return ResponseEntity.ok(employeeService.getEmployeeById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeDTO> updateEmployee(@PathVariable Long id, @RequestBody EmployeeDTO dto) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, dto));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> toggleStatus(@PathVariable Long id) {
        employeeService.toggleEmployeeStatus(id);
        return ResponseEntity.ok("Status updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.ok("Employee deleted successfully");
    }

    /**
     * Dedicated endpoint for Admin to configure salary for an employee.
     * Returns a clear error if salary values are invalid.
     */
    @PostMapping("/{id}/salary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateSalary(@PathVariable Long id, @RequestBody Map<String, Double> body) {
        try {
            Double basicSalary = body.get("basicSalary");
            Double allowances = body.getOrDefault("allowances", 0.0);
            Double deductions = body.getOrDefault("deductions", 0.0);
            EmployeeDTO result = employeeService.updateEmployeeSalary(id, basicSalary, allowances, deductions);
            return ResponseEntity.ok(result);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    /**
     * Admin-only: reset the password for a specific employee's user account.
     * Accepts { "newPassword": "...", "confirmPassword": "..." }.
     * Passwords are BCrypt-hashed in the service layer before being saved.
     * Plain-text passwords are never stored or returned.
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resetPassword(@PathVariable Long id,
                                           @RequestBody Map<String, String> body) {
        try {
            String newPassword = body.get("newPassword");
            String confirmPassword = body.get("confirmPassword");
            employeeService.resetPassword(id, newPassword, confirmPassword);
            return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}

package com.employeetrack.controller;

import com.employeetrack.dto.PayrollDTO;
import com.employeetrack.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    @PostMapping("/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> processPayroll(@RequestBody Map<String, Integer> request) {
        int month = request.get("month");
        int year = request.get("year");
        List<PayrollDTO> result = payrollService.processMonthlyPayroll(month, year);

        long zeroCount = result.stream()
            .filter(p -> p.getBasicSalary() == null || p.getBasicSalary() == 0.0)
            .count();

        if (zeroCount > 0) {
            return ResponseEntity.ok(Map.of(
                "payrolls", result,
                "warning", zeroCount + " employee(s) processed with ₹0 basic salary. "
                    + "Please configure salary details in Employee Management before re-processing payroll.",
                "zeroSalaryCount", zeroCount
            ));
        }

        return ResponseEntity.ok(Map.of(
            "payrolls", result,
            "warning", "",
            "zeroSalaryCount", 0
        ));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<List<PayrollDTO>> getAllPayrolls() {
        return ResponseEntity.ok(payrollService.getAllPayrolls());
    }

    /**
     * Secure endpoint: returns payroll for the currently authenticated employee only.
     * Employees cannot access other employees' salary data through this endpoint.
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<List<PayrollDTO>> getMyPayroll() {
        return ResponseEntity.ok(payrollService.getMyPayroll());
    }

    /**
     * Salary Insights for the currently authenticated employee.
     * Compares the two most-recent payroll records from MySQL and returns
     * component-level differences with a plain-language explanation.
     * Access is restricted to EMPLOYEE role only — employees see only their own data.
     */
    @GetMapping("/my/insights")
    @PreAuthorize("hasRole('EMPLOYEE')")
    public ResponseEntity<Map<String, Object>> getMySalaryInsights() {
        return ResponseEntity.ok(payrollService.getSalaryInsights());
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    public ResponseEntity<List<PayrollDTO>> getPayrollByEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(payrollService.getPayrollByEmployee(employeeId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'EMPLOYEE')")
    public ResponseEntity<PayrollDTO> getPayrollById(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.getPayrollById(id));
    }

    /**
     * Returns a count of active employees who have no salary configured (basicSalary = 0).
     * Used by the admin payroll page to warn before processing.
     */
    @GetMapping("/salary-config-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getSalaryConfigStatus() {
        long count = payrollService.countEmployeesWithoutSalary();
        return ResponseEntity.ok(Map.of(
            "employeesWithoutSalary", count,
            "message", count > 0
                ? count + " employee(s) have no salary configured. Configure salary in Employee Management before processing payroll."
                : "All active employees have salary configured."
        ));
    }

    /**
     * Corrects zero-value payroll records for a specific month/year using the employee's
     * currently configured salary from the database.
     *
     * Body: { "month": 10, "year": 2026, "forcePaid": false }
     *
     * - forcePaid=false (default): Only corrects GENERATED records. PAID records are skipped.
     * - forcePaid=true: Also corrects PAID records (explicit admin override).
     * - Does NOT create duplicate records or touch records that already have correct salary.
     */
    @PostMapping("/correct")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> correctPayroll(@RequestBody Map<String, Object> request) {
        try {
            int month = (Integer) request.get("month");
            int year = (Integer) request.get("year");
            boolean forcePaid = request.containsKey("forcePaid") && Boolean.TRUE.equals(request.get("forcePaid"));
            return ResponseEntity.ok(payrollService.correctPayroll(month, year, forcePaid));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}

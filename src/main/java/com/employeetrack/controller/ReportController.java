package com.employeetrack.controller;

import com.employeetrack.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'HR')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/payroll/monthly")
    public ResponseEntity<Map<String, Object>> getMonthlyPayrollReport(
            @RequestParam int month, @RequestParam int year) {
        return ResponseEntity.ok(reportService.getMonthlyPayrollReport(month, year));
    }

    @GetMapping("/payroll/department")
    public ResponseEntity<Map<String, Object>> getDepartmentPayrollReport() {
        return ResponseEntity.ok(reportService.getDepartmentPayrollReport());
    }

    @GetMapping("/attendance")
    public ResponseEntity<Map<String, Object>> getAttendanceReport() {
        return ResponseEntity.ok(reportService.getAttendanceReport());
    }

    @GetMapping("/leaves")
    public ResponseEntity<Map<String, Object>> getLeaveReport() {
        return ResponseEntity.ok(reportService.getLeaveReport());
    }

    @GetMapping("/payroll/summary")
    public ResponseEntity<Map<String, Object>> getPayrollSummary() {
        return ResponseEntity.ok(reportService.getPayrollSummary());
    }
}

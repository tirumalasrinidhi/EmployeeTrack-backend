package com.employeetrack.controller;

import com.employeetrack.dto.AttendanceDTO;
import com.employeetrack.entity.Employee;
import com.employeetrack.entity.User;
import com.employeetrack.repository.EmployeeRepository;
import com.employeetrack.repository.UserRepository;
import com.employeetrack.service.AttendanceService;
import com.employeetrack.service.LeaveService;
import com.employeetrack.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AttendanceService attendanceService;
    private final LeaveService leaveService;
    private final ReportService reportService;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    @GetMapping("/employee")
    public ResponseEntity<Map<String, Object>> getEmployeeDashboard() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email).orElseThrow();
        Employee emp = employeeRepository.findByUser(user).orElseThrow();

        // Today's attendance DTO (null if no record yet)
        AttendanceDTO todayAtt = attendanceService.getMyTodayAttendance();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("employeeId", emp.getEmployeeId());
        data.put("name", user.getName());
        data.put("department", emp.getDepartment() != null ? emp.getDepartment().getName() : "N/A");
        data.put("designation", emp.getDesignation());
        data.put("basicSalary", emp.getBasicSalary());
        data.put("netSalary", emp.getBasicSalary() + emp.getAllowances() - emp.getDeductions());
        data.put("myAttendanceCount", attendanceService.countMyAttendance());
        data.put("myPendingLeaves", leaveService.getMyLeaves()
            .stream().filter(l -> l.getStatus().name().equals("PENDING")).count());
        // Today's attendance card — includes workingDuration in HH:mm:ss
        data.put("todayAttendance", todayAtt);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/admin")
    public ResponseEntity<Map<String, Object>> getAdminDashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalEmployees", employeeRepository.count());
        data.put("activeEmployees", employeeRepository.countByActive(true));
        data.put("presentToday", attendanceService.countPresentToday());
        data.put("pendingLeaves", leaveService.countPendingLeaves());
        data.put("payrollSummary", reportService.getPayrollSummary());
        return ResponseEntity.ok(data);
    }

    @GetMapping("/hr")
    public ResponseEntity<Map<String, Object>> getHrDashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalEmployees", employeeRepository.count());
        data.put("activeEmployees", employeeRepository.countByActive(true));
        data.put("presentToday", attendanceService.countPresentToday());
        data.put("pendingLeaves", leaveService.countPendingLeaves());
        data.put("payrollSummary", reportService.getPayrollSummary());
        return ResponseEntity.ok(data);
    }
}

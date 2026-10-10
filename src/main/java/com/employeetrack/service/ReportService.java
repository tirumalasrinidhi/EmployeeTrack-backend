package com.employeetrack.service;

import com.employeetrack.dto.PayrollDTO;
import com.employeetrack.entity.*;
import com.employeetrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRepository;
    private final DepartmentRepository departmentRepository;
    private final PayslipRepository payslipRepository;

    public Map<String, Object> getMonthlyPayrollReport(int month, int year) {
        List<Payroll> payrolls = payrollRepository.findByMonthAndYear(month, year);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("month", month);
        report.put("year", year);
        report.put("totalEmployees", payrolls.size());
        report.put("totalBasicSalary", payrolls.stream().mapToDouble(Payroll::getBasicSalary).sum());
        report.put("totalAllowances", payrolls.stream().mapToDouble(Payroll::getAllowances).sum());
        report.put("totalDeductions", payrolls.stream().mapToDouble(Payroll::getDeductions).sum());
        report.put("totalNetSalary", payrolls.stream().mapToDouble(Payroll::getNetSalary).sum());

        List<Map<String, Object>> details = payrolls.stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", p.getEmployee().getEmployeeId());
            row.put("employeeName", p.getEmployee().getUser().getName());
            row.put("department", p.getEmployee().getDepartment() != null ? p.getEmployee().getDepartment().getName() : "N/A");
            row.put("basicSalary", p.getBasicSalary());
            row.put("allowances", p.getAllowances());
            row.put("deductions", p.getDeductions());
            row.put("netSalary", p.getNetSalary());
            return row;
        }).collect(Collectors.toList());
        report.put("details", details);

        return report;
    }

    public Map<String, Object> getDepartmentPayrollReport() {
        List<Department> departments = departmentRepository.findAll();
        List<Map<String, Object>> deptReports = new ArrayList<>();

        for (Department dept : departments) {
            List<Employee> emps = employeeRepository.findByDepartmentId(dept.getId());
            double totalSalary = emps.stream()
                .mapToDouble(e -> e.getBasicSalary() + e.getAllowances() - e.getDeductions())
                .sum();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("departmentId", dept.getId());
            row.put("departmentName", dept.getName());
            row.put("employeeCount", emps.size());
            row.put("totalNetSalary", totalSalary);
            deptReports.add(row);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("departments", deptReports);
        return report;
    }

    public Map<String, Object> getAttendanceReport() {
        List<Attendance> allAttendance = attendanceRepository.findAll();
        long present = allAttendance.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
        long absent = allAttendance.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT).count();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalRecords", allAttendance.size());
        report.put("presentCount", present);
        report.put("absentCount", absent);
        report.put("todayPresent", attendanceRepository.countPresentByDate(LocalDate.now()));

        List<Map<String, Object>> details = allAttendance.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", a.getEmployee().getEmployeeId());
            row.put("employeeName", a.getEmployee().getUser().getName());
            row.put("date", a.getDate());
            row.put("checkIn", a.getCheckIn());
            row.put("checkOut", a.getCheckOut());
            row.put("workingHours", a.getWorkingHours());
            row.put("status", a.getStatus());
            return row;
        }).collect(Collectors.toList());
        report.put("details", details);

        return report;
    }

    public Map<String, Object> getLeaveReport() {
        List<LeaveRequest> all = leaveRepository.findAll();
        long pending = all.stream().filter(l -> l.getStatus() == LeaveRequest.LeaveStatus.PENDING).count();
        long approved = all.stream().filter(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED).count();
        long rejected = all.stream().filter(l -> l.getStatus() == LeaveRequest.LeaveStatus.REJECTED).count();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("total", all.size());
        report.put("pending", pending);
        report.put("approved", approved);
        report.put("rejected", rejected);

        List<Map<String, Object>> details = all.stream().map(lr -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeId", lr.getEmployee().getEmployeeId());
            row.put("employeeName", lr.getEmployee().getUser().getName());
            row.put("leaveType", lr.getLeaveType());
            row.put("fromDate", lr.getFromDate());
            row.put("toDate", lr.getToDate());
            row.put("days", lr.getNumberOfDays());
            row.put("status", lr.getStatus());
            return row;
        }).collect(Collectors.toList());
        report.put("details", details);

        return report;
    }

    public Map<String, Object> getPayrollSummary() {
        List<Employee> allEmployees = employeeRepository.findAll();
        long active = allEmployees.stream().filter(Employee::isActive).count();
        double totalBasic = allEmployees.stream().mapToDouble(Employee::getBasicSalary).sum();
        double totalAllowances = allEmployees.stream().mapToDouble(Employee::getAllowances).sum();
        double totalDeductions = allEmployees.stream().mapToDouble(Employee::getDeductions).sum();
        double totalNet = totalBasic + totalAllowances - totalDeductions;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEmployees", allEmployees.size());
        summary.put("activeEmployees", active);
        summary.put("totalBasicSalary", totalBasic);
        summary.put("totalAllowances", totalAllowances);
        summary.put("totalDeductions", totalDeductions);
        summary.put("totalNetPayroll", totalNet);
        return summary;
    }
}

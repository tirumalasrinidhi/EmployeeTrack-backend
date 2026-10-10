package com.employeetrack.service;

import com.employeetrack.dto.PayrollDTO;
import com.employeetrack.entity.Employee;
import com.employeetrack.entity.Payroll;
import com.employeetrack.entity.Payslip;
import com.employeetrack.entity.User;
import com.employeetrack.repository.EmployeeRepository;
import com.employeetrack.repository.PayrollRepository;
import com.employeetrack.repository.PayslipRepository;
import com.employeetrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollService {

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
    private final PayslipRepository payslipRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<PayrollDTO> processMonthlyPayroll(int month, int year) {
        List<Employee> employees = employeeRepository.findAll()
            .stream().filter(Employee::isActive).collect(Collectors.toList());

        if (employees.isEmpty()) {
            throw new RuntimeException("No active employees found to process payroll.");
        }

        return employees.stream().map(emp -> {
            double basic = emp.getBasicSalary() != null ? emp.getBasicSalary() : 0.0;
            double allowances = emp.getAllowances() != null ? emp.getAllowances() : 0.0;
            double deductions = emp.getDeductions() != null ? emp.getDeductions() : 0.0;
            double netSalary = basic + allowances - deductions;

            // Check if already processed for this month/year
            Optional<Payroll> existing = payrollRepository.findByEmployeeAndMonthAndYear(emp, month, year);

            if (existing.isPresent()) {
                Payroll existingPayroll = existing.get();
                // Null-safe check: if the existing record has zero or null basicSalary
                // and the employee now has a configured salary, update the record.
                boolean isZeroSalary = existingPayroll.getBasicSalary() == null
                    || existingPayroll.getBasicSalary() == 0.0;
                if (isZeroSalary && basic > 0) {
                    existingPayroll.setBasicSalary(basic);
                    existingPayroll.setAllowances(allowances);
                    existingPayroll.setDeductions(deductions);
                    existingPayroll.setNetSalary(netSalary);
                    existingPayroll = payrollRepository.save(existingPayroll);

                    // Update the associated payslip if it exists
                    Optional<Payslip> existingPayslip = payslipRepository.findByPayrollId(existingPayroll.getId());
                    if (existingPayslip.isPresent()) {
                        Payslip ps = existingPayslip.get();
                        ps.setBasicSalary(basic);
                        ps.setAllowances(allowances);
                        ps.setDeductions(deductions);
                        ps.setNetSalary(netSalary);
                        payslipRepository.save(ps);
                    }
                }
                return toDTO(existingPayroll);
            }

            // Validate: warn if salary is zero but still allow processing
            // (Admin may have intentionally set zero; the message shown to admin is handled at controller level)
            Payroll payroll = Payroll.builder()
                .employee(emp)
                .month(month)
                .year(year)
                .basicSalary(basic)
                .allowances(allowances)
                .deductions(deductions)
                .netSalary(netSalary)
                .status(Payroll.PayrollStatus.GENERATED)
                .build();
            payroll = payrollRepository.save(payroll);

            // Auto-generate payslip
            Payslip payslip = Payslip.builder()
                .payroll(payroll)
                .employee(emp)
                .month(month)
                .year(year)
                .basicSalary(basic)
                .allowances(allowances)
                .deductions(deductions)
                .netSalary(netSalary)
                .build();
            payslipRepository.save(payslip);

            return toDTO(payroll);
        }).collect(Collectors.toList());
    }

    public List<PayrollDTO> getAllPayrolls() {
        return payrollRepository.findAll().stream()
            .map(this::toDTO).collect(Collectors.toList());
    }

    public List<PayrollDTO> getPayrollByEmployee(Long employeeId) {
        return payrollRepository.findByEmployeeId(employeeId)
            .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * Returns payroll records for the currently authenticated employee only.
     * Ensures employees cannot access other employees' salary data.
     */
    public List<PayrollDTO> getMyPayroll() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        Employee emp = employeeRepository.findByUser(user)
            .orElseThrow(() -> new RuntimeException("Employee profile not found for user: " + email));
        return payrollRepository.findByEmployeeId(emp.getId())
            .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public PayrollDTO getPayrollById(Long id) {
        Payroll p = payrollRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Payroll not found"));
        return toDTO(p);
    }

    public Double getMonthlyPayrollTotal(int month, int year) {
        Double total = payrollRepository.sumNetSalaryByMonthAndYear(month, year);
        return total != null ? total : 0.0;
    }

    /**
     * Count how many active employees have salary NOT configured (basicSalary = 0 or null).
     */
    public long countEmployeesWithoutSalary() {
        return employeeRepository.findAll().stream()
            .filter(Employee::isActive)
            .filter(emp -> emp.getBasicSalary() == null || emp.getBasicSalary() == 0.0)
            .count();
    }

    /**
     * Corrects payroll records for a given month and year where basicSalary is zero or null,
     * using the employee's currently configured salary from the database.
     *
     * Rules:
     *  - Only corrects records where basicSalary is 0 or null.
     *  - If a record has status PAID and forcePaid=false, it is skipped (requires explicit authorization).
     *  - If forcePaid=true, PAID records are also corrected.
     *  - Does NOT create duplicate records.
     *  - Returns a summary of what was corrected vs skipped.
     */
    @Transactional
    public Map<String, Object> correctPayroll(int month, int year, boolean forcePaid) {
        List<Payroll> allPayrolls = payrollRepository.findByMonthAndYear(month, year);

        if (allPayrolls.isEmpty()) {
            throw new RuntimeException(
                "No payroll records found for " + month + "/" + year +
                ". Process payroll first, then correct."
            );
        }

        List<PayrollDTO> corrected = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> alreadyCorrect = new ArrayList<>();

        for (Payroll payroll : allPayrolls) {
            Employee emp = payroll.getEmployee();
            String empLabel = emp.getEmployeeId() + " (" + emp.getUser().getName() + ")";

            // Only correct zero / null basicSalary records
            boolean isZeroSalary = payroll.getBasicSalary() == null || payroll.getBasicSalary() == 0.0;
            if (!isZeroSalary) {
                alreadyCorrect.add(empLabel);
                continue;
            }

            // Require explicit authorization to correct PAID records
            if (payroll.getStatus() == Payroll.PayrollStatus.PAID && !forcePaid) {
                skipped.add(empLabel + " [PAID — requires forcePaid=true to correct]");
                continue;
            }

            // Validate: employee must have salary configured
            double basic = emp.getBasicSalary() != null ? emp.getBasicSalary() : 0.0;
            if (basic <= 0) {
                skipped.add(empLabel + " [salary not configured on employee record]");
                continue;
            }

            double allowances = emp.getAllowances() != null ? emp.getAllowances() : 0.0;
            double deductions = emp.getDeductions() != null ? emp.getDeductions() : 0.0;
            double netSalary = basic + allowances - deductions;

            // Update payroll record with correct values from employee's configured salary
            payroll.setBasicSalary(basic);
            payroll.setAllowances(allowances);
            payroll.setDeductions(deductions);
            payroll.setNetSalary(netSalary);
            payroll = payrollRepository.save(payroll);

            // Update associated payslip
            Optional<Payslip> existingPayslip = payslipRepository.findByPayrollId(payroll.getId());
            if (existingPayslip.isPresent()) {
                Payslip ps = existingPayslip.get();
                ps.setBasicSalary(basic);
                ps.setAllowances(allowances);
                ps.setDeductions(deductions);
                ps.setNetSalary(netSalary);
                payslipRepository.save(ps);
            } else {
                // Create payslip if it doesn't exist yet
                Payslip ps = Payslip.builder()
                    .payroll(payroll)
                    .employee(emp)
                    .month(month)
                    .year(year)
                    .basicSalary(basic)
                    .allowances(allowances)
                    .deductions(deductions)
                    .netSalary(netSalary)
                    .build();
                payslipRepository.save(ps);
            }

            corrected.add(toDTO(payroll));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("corrected", corrected);
        result.put("correctedCount", corrected.size());
        result.put("skipped", skipped);
        result.put("skippedCount", skipped.size());
        result.put("alreadyCorrect", alreadyCorrect);
        result.put("alreadyCorrectCount", alreadyCorrect.size());
        result.put("month", month);
        result.put("year", year);
        return result;
    }

    private PayrollDTO toDTO(Payroll p) {
        Optional<Payslip> payslip = payslipRepository.findByPayrollId(p.getId());
        return PayrollDTO.builder()
            .id(p.getId())
            .employeeDbId(p.getEmployee().getId())
            .employeeId(p.getEmployee().getEmployeeId())
            .employeeName(p.getEmployee().getUser().getName())
            .departmentName(p.getEmployee().getDepartment() != null ? p.getEmployee().getDepartment().getName() : null)
            .designation(p.getEmployee().getDesignation())
            .month(p.getMonth())
            .year(p.getYear())
            .basicSalary(p.getBasicSalary())
            .allowances(p.getAllowances())
            .deductions(p.getDeductions())
            .netSalary(p.getNetSalary())
            .status(p.getStatus())
            .generatedAt(p.getGeneratedAt())
            .payslipId(payslip.map(Payslip::getId).orElse(null))
            .build();
    }

    /**
     * Salary Insights: compare the current employee's most-recent payroll record
     * with the one immediately before it.
     *
     * All values come from actual MySQL payroll rows — no hardcoding.
     * Returns a structured map that the Employee Dashboard renders as "Salary Insights".
     *
     * The method distinguishes three states for each period:
     *   - record exists with salary > 0  → show values
     *   - record exists with salary = 0  → "Processed but salary was not configured"
     *   - no record at all               → "No payroll record found for this period"
     */
    public Map<String, Object> getSalaryInsights() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        Employee emp = employeeRepository.findByUser(user)
            .orElseThrow(() -> new RuntimeException("Employee profile not found"));

        // Fetch all payroll records for this employee, sorted by year DESC then month DESC
        List<Payroll> all = payrollRepository.findByEmployeeId(emp.getId())
            .stream()
            .sorted((a, b) -> {
                if (!b.getYear().equals(a.getYear())) return b.getYear() - a.getYear();
                return b.getMonth() - a.getMonth();
            })
            .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();

        if (all.isEmpty()) {
            result.put("hasInsights", false);
            result.put("message", "No payroll records found. Insights will appear once payroll is processed.");
            return result;
        }

        Payroll current = all.get(0);
        Payroll previous = all.size() >= 2 ? all.get(1) : null;

        String[] MONTHS = {"", "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"};

        // Current month data
        Map<String, Object> curr = new HashMap<>();
        curr.put("month", current.getMonth());
        curr.put("year", current.getYear());
        curr.put("label", MONTHS[current.getMonth()] + " " + current.getYear());
        curr.put("basicSalary", current.getBasicSalary());
        curr.put("allowances", current.getAllowances());
        curr.put("deductions", current.getDeductions());
        curr.put("netSalary", current.getNetSalary());
        curr.put("status", current.getStatus().name());
        boolean currZero = current.getBasicSalary() == null || current.getBasicSalary() == 0.0;
        curr.put("salaryConfigured", !currZero);

        result.put("current", curr);
        result.put("hasInsights", true);

        if (previous == null) {
            result.put("previous", null);
            result.put("hasPrevious", false);
            result.put("message", "Only one payroll record found. Comparison will appear next month.");
            return result;
        }

        // Previous month data
        Map<String, Object> prev = new HashMap<>();
        prev.put("month", previous.getMonth());
        prev.put("year", previous.getYear());
        prev.put("label", MONTHS[previous.getMonth()] + " " + previous.getYear());
        prev.put("basicSalary", previous.getBasicSalary());
        prev.put("allowances", previous.getAllowances());
        prev.put("deductions", previous.getDeductions());
        prev.put("netSalary", previous.getNetSalary());
        prev.put("status", previous.getStatus().name());
        boolean prevZero = previous.getBasicSalary() == null || previous.getBasicSalary() == 0.0;
        prev.put("salaryConfigured", !prevZero);

        result.put("previous", prev);
        result.put("hasPrevious", true);

        // Differences (current minus previous) — rounded to 2 decimal places
        double dBasic      = round2(safeVal(current.getBasicSalary()) - safeVal(previous.getBasicSalary()));
        double dAllowances = round2(safeVal(current.getAllowances())  - safeVal(previous.getAllowances()));
        double dDeductions = round2(safeVal(current.getDeductions())  - safeVal(previous.getDeductions()));
        double dNet        = round2(safeVal(current.getNetSalary())   - safeVal(previous.getNetSalary()));

        Map<String, Object> diff = new HashMap<>();
        diff.put("basicSalary",  dBasic);
        diff.put("allowances",   dAllowances);
        diff.put("deductions",   dDeductions);
        diff.put("netSalary",    dNet);
        diff.put("direction",    dNet > 0 ? "INCREASE" : dNet < 0 ? "DECREASE" : "NO_CHANGE");

        result.put("diff", diff);

        // Human-readable explanation built from actual differences
        result.put("explanation", buildExplanation(dNet, dBasic, dAllowances, dDeductions, currZero, prevZero));

        return result;
    }

    private static double safeVal(Double v) { return v != null ? v : 0.0; }
    private static double round2(double v)  { return Math.round(v * 100.0) / 100.0; }

    private static String buildExplanation(double dNet, double dBasic, double dAllowances,
                                           double dDeductions, boolean currZero, boolean prevZero) {
        if (currZero && prevZero) return "Both months have ₹0 salary — salary has not been configured yet.";
        if (currZero) return "Current month's salary is ₹0 — salary may not have been configured when payroll was processed.";
        if (prevZero) return "Previous month's salary was ₹0 — comparison may not reflect a true change.";

        if (dNet == 0 && dBasic == 0 && dAllowances == 0 && dDeductions == 0) {
            return "Your salary is unchanged compared to last month.";
        }

        StringBuilder sb = new StringBuilder();
        if (dNet > 0) sb.append("Your net salary increased by ₹").append(String.format("%.2f", dNet)).append(". ");
        else if (dNet < 0) sb.append("Your net salary decreased by ₹").append(String.format("%.2f", Math.abs(dNet))).append(". ");

        if (dBasic != 0)      sb.append("Basic salary ").append(dBasic > 0 ? "increased" : "decreased").append(" by ₹").append(String.format("%.2f", Math.abs(dBasic))).append(". ");
        if (dAllowances != 0) sb.append("Allowances ").append(dAllowances > 0 ? "increased" : "decreased").append(" by ₹").append(String.format("%.2f", Math.abs(dAllowances))).append(". ");
        if (dDeductions != 0) sb.append("Deductions ").append(dDeductions > 0 ? "increased" : "decreased").append(" by ₹").append(String.format("%.2f", Math.abs(dDeductions))).append(". ");

        return sb.toString().trim();
    }
}


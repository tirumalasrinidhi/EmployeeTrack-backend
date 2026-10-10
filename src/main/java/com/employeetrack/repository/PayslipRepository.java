package com.employeetrack.repository;

import com.employeetrack.entity.Employee;
import com.employeetrack.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    List<Payslip> findByEmployee(Employee employee);
    List<Payslip> findByEmployeeId(Long employeeId);
    Optional<Payslip> findByPayrollId(Long payrollId);
}

package com.employeetrack.repository;

import com.employeetrack.entity.Employee;
import com.employeetrack.entity.Payroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {
    List<Payroll> findByEmployee(Employee employee);
    List<Payroll> findByEmployeeId(Long employeeId);
    List<Payroll> findByMonthAndYear(Integer month, Integer year);
    Optional<Payroll> findByEmployeeAndMonthAndYear(Employee employee, Integer month, Integer year);

    @Query("SELECT SUM(p.netSalary) FROM Payroll p WHERE p.month = :month AND p.year = :year")
    Double sumNetSalaryByMonthAndYear(@Param("month") int month, @Param("year") int year);

    @Query("SELECT SUM(p.netSalary) FROM Payroll p WHERE p.year = :year")
    Double sumNetSalaryByYear(@Param("year") int year);
}

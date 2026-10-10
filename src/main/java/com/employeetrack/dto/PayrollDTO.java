package com.employeetrack.dto;

import com.employeetrack.entity.Payroll;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayrollDTO {
    private Long id;
    private Long employeeDbId;
    private String employeeId;
    private String employeeName;
    private String departmentName;
    private String designation;
    private Integer month;
    private Integer year;
    private Double basicSalary;
    private Double allowances;
    private Double deductions;
    private Double netSalary;
    private Payroll.PayrollStatus status;
    private LocalDateTime generatedAt;
    private Long payslipId;
}

package com.employeetrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeDTO {
    private Long id;
    private String employeeId;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String designation;
    private Long departmentId;
    private String departmentName;
    private LocalDate dateOfJoining;
    private String bankName;
    private String accountNumber;
    private String ifscCode;
    private String panNumber;
    private Double basicSalary;
    private Double allowances;
    private Double deductions;
    private boolean active;
}

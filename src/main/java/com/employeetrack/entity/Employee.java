package com.employeetrack.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "employees")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", unique = true, nullable = false)
    private String employeeId;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    private String designation;

    private String phone;

    private String address;

    @Column(name = "date_of_joining")
    private LocalDate dateOfJoining;

    // Bank details
    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "ifsc_code")
    private String ifscCode;

    @Column(name = "pan_number")
    private String panNumber;

    @Column(name = "basic_salary")
    @Builder.Default
    private Double basicSalary = 0.0;

    @Column(name = "allowances")
    @Builder.Default
    private Double allowances = 0.0;

    @Column(name = "deductions")
    @Builder.Default
    private Double deductions = 0.0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}

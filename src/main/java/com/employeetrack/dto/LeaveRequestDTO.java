package com.employeetrack.dto;

import com.employeetrack.entity.LeaveRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRequestDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeIdCode;
    private String departmentName;
    private LeaveRequest.LeaveType leaveType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer numberOfDays;
    private String reason;
    private LeaveRequest.LeaveStatus status;
    private LocalDateTime appliedAt;
    private String approvedBy;
}

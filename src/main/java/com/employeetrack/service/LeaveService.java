package com.employeetrack.service;

import com.employeetrack.dto.LeaveRequestDTO;
import com.employeetrack.entity.Employee;
import com.employeetrack.entity.LeaveRequest;
import com.employeetrack.entity.User;
import com.employeetrack.repository.EmployeeRepository;
import com.employeetrack.repository.LeaveRequestRepository;
import com.employeetrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveRequestRepository leaveRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    private Employee getCurrentEmployee() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return employeeRepository.findByUser(user)
            .orElseThrow(() -> new RuntimeException("Employee not found"));
    }

    @Transactional
    public LeaveRequestDTO applyLeave(LeaveRequest request) {
        Employee employee = getCurrentEmployee();

        if (request.getToDate().isBefore(request.getFromDate())) {
            throw new RuntimeException("To date cannot be before From date");
        }

        long days = ChronoUnit.DAYS.between(request.getFromDate(), request.getToDate()) + 1;
        request.setEmployee(employee);
        request.setNumberOfDays((int) days);
        request.setStatus(LeaveRequest.LeaveStatus.PENDING);

        return toDTO(leaveRepository.save(request));
    }

    public List<LeaveRequestDTO> getMyLeaves() {
        Employee employee = getCurrentEmployee();
        return leaveRepository.findByEmployeeOrderByAppliedAtDesc(employee)
            .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<LeaveRequestDTO> getAllLeaves() {
        return leaveRepository.findAllByOrderByAppliedAtDesc()
            .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public LeaveRequestDTO approveLeave(Long id) {
        LeaveRequest leave = leaveRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Leave request not found"));
        String approver = SecurityContextHolder.getContext().getAuthentication().getName();
        leave.setStatus(LeaveRequest.LeaveStatus.APPROVED);
        leave.setApprovedBy(approver);
        return toDTO(leaveRepository.save(leave));
    }

    @Transactional
    public LeaveRequestDTO rejectLeave(Long id) {
        LeaveRequest leave = leaveRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Leave request not found"));
        String approver = SecurityContextHolder.getContext().getAuthentication().getName();
        leave.setStatus(LeaveRequest.LeaveStatus.REJECTED);
        leave.setApprovedBy(approver);
        return toDTO(leaveRepository.save(leave));
    }

    public long countPendingLeaves() {
        return leaveRepository.countByStatus(LeaveRequest.LeaveStatus.PENDING);
    }

    private LeaveRequestDTO toDTO(LeaveRequest lr) {
        return LeaveRequestDTO.builder()
            .id(lr.getId())
            .employeeId(lr.getEmployee().getId())
            .employeeName(lr.getEmployee().getUser().getName())
            .employeeIdCode(lr.getEmployee().getEmployeeId())
            .departmentName(lr.getEmployee().getDepartment() != null ? lr.getEmployee().getDepartment().getName() : null)
            .leaveType(lr.getLeaveType())
            .fromDate(lr.getFromDate())
            .toDate(lr.getToDate())
            .numberOfDays(lr.getNumberOfDays())
            .reason(lr.getReason())
            .status(lr.getStatus())
            .appliedAt(lr.getAppliedAt())
            .approvedBy(lr.getApprovedBy())
            .build();
    }
}

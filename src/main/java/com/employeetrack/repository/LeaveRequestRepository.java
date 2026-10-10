package com.employeetrack.repository;

import com.employeetrack.entity.Employee;
import com.employeetrack.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByEmployeeOrderByAppliedAtDesc(Employee employee);
    List<LeaveRequest> findByStatusOrderByAppliedAtDesc(LeaveRequest.LeaveStatus status);
    long countByStatus(LeaveRequest.LeaveStatus status);
    List<LeaveRequest> findByEmployee(Employee employee);
    List<LeaveRequest> findAllByOrderByAppliedAtDesc();
}

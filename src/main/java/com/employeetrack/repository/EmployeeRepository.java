package com.employeetrack.repository;

import com.employeetrack.entity.Employee;
import com.employeetrack.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByUser(User user);
    Optional<Employee> findByEmployeeId(String employeeId);
    Optional<Employee> findByUserId(Long userId);
    List<Employee> findByDepartmentId(Long departmentId);
    boolean existsByEmployeeId(String employeeId);
    boolean existsByPhone(String phone);
    long countByActive(boolean active);
}


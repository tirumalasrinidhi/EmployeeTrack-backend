package com.employeetrack.service;

import com.employeetrack.entity.Department;
import com.employeetrack.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    public Department getDepartmentById(Long id) {
        return departmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Department not found"));
    }

    @Transactional
    public Department createDepartment(Department dept) {
        if (departmentRepository.existsByName(dept.getName())) {
            throw new RuntimeException("Department with this name already exists");
        }
        return departmentRepository.save(dept);
    }

    @Transactional
    public Department updateDepartment(Long id, Department dto) {
        Department dept = departmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Department not found"));
        dept.setName(dto.getName());
        dept.setDescription(dto.getDescription());
        return departmentRepository.save(dept);
    }

    @Transactional
    public void deleteDepartment(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new RuntimeException("Department not found");
        }
        departmentRepository.deleteById(id);
    }
}

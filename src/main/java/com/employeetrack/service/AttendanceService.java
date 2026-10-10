package com.employeetrack.service;

import com.employeetrack.dto.AttendanceDTO;
import com.employeetrack.entity.Attendance;
import com.employeetrack.entity.Employee;
import com.employeetrack.entity.User;
import com.employeetrack.repository.AttendanceRepository;
import com.employeetrack.repository.EmployeeRepository;
import com.employeetrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── helpers ────────────────────────────────────────────────────────────

    private Employee getCurrentEmployee() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
        return employeeRepository.findByUser(user)
            .orElseThrow(() -> new RuntimeException("Employee not found"));
    }

    /**
     * Convert a LocalTime to "HH:mm:ss" string. Returns null for null input.
     */
    private static String fmtTime(LocalTime t) {
        return t == null ? null : t.format(TIME_FMT);
    }

    /**
     * Compute working duration directly from two LocalTime timestamps and
     * return it as "HH:mm:ss".
     *
     * WHY: The stored workingHours (Double) is rounded to 2 decimal places
     * (e.g. 17 seconds → 0.0047 h → rounds to 0.00), losing all precision
     * for short sessions. Computing from the original timestamps avoids this
     * loss entirely and matches the actual database values precisely.
     *
     * Handles sessions where checkOut < checkIn (overnight/clock-skew) by
     * clamping to 0 rather than producing a negative duration.
     */
    private static String computeWorkingDuration(LocalTime checkIn, LocalTime checkOut) {
        if (checkIn == null || checkOut == null) return null;
        Duration duration = Duration.between(checkIn, checkOut);
        long totalSeconds = duration.toSeconds();
        if (totalSeconds < 0) totalSeconds = 0; // guard against overnight / clock skew
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /**
     * Build an AttendanceDTO from an Attendance entity.
     *
     * workingDuration is computed DIRECTLY from the stored checkIn/checkOut
     * LocalTime fields (not from the rounded workingHours Double) to ensure
     * accuracy for short and long sessions alike.
     */
    public AttendanceDTO toDTO(Attendance a) {
        Employee emp = a.getEmployee();
        return AttendanceDTO.builder()
            .id(a.getId())
            .employeeDbId(emp.getId())
            .employeeId(emp.getEmployeeId())
            .employeeName(emp.getUser().getName())
            .departmentName(emp.getDepartment() != null ? emp.getDepartment().getName() : null)
            .date(a.getDate().toString())
            .checkIn(fmtTime(a.getCheckIn()))
            .checkOut(fmtTime(a.getCheckOut()))
            // Compute duration from actual timestamps, not from the stored decimal
            .workingDuration(computeWorkingDuration(a.getCheckIn(), a.getCheckOut()))
            .workingHours(a.getWorkingHours())
            .status(a.getStatus().name())
            .build();
    }

    // ── transactional operations ────────────────────────────────────────────

    @Transactional
    public AttendanceDTO checkIn() {
        Employee employee = getCurrentEmployee();
        LocalDate today = LocalDate.now();

        Optional<Attendance> existing = attendanceRepository.findByEmployeeAndDate(employee, today);
        if (existing.isPresent() && existing.get().getCheckIn() != null) {
            throw new RuntimeException("Already checked in today");
        }

        Attendance attendance = existing.orElseGet(() -> Attendance.builder()
            .employee(employee)
            .date(today)
            .build());

        attendance.setCheckIn(LocalTime.now());
        attendance.setStatus(Attendance.AttendanceStatus.PRESENT);
        return toDTO(attendanceRepository.save(attendance));
    }

    @Transactional
    public AttendanceDTO checkOut() {
        Employee employee = getCurrentEmployee();
        LocalDate today = LocalDate.now();

        Attendance attendance = attendanceRepository.findByEmployeeAndDate(employee, today)
            .orElseThrow(() -> new RuntimeException("No check-in found for today"));

        if (attendance.getCheckIn() == null) {
            throw new RuntimeException("You haven't checked in yet");
        }
        if (attendance.getCheckOut() != null) {
            throw new RuntimeException("Already checked out today");
        }

        LocalTime checkOutTime = LocalTime.now();
        attendance.setCheckOut(checkOutTime);

        // Store working_hours in the DB with full precision (no rounding to 2dp)
        // so historical records can be compared by decimal if needed.
        // The displayed duration is always re-derived from checkIn/checkOut in toDTO().
        Duration duration = Duration.between(attendance.getCheckIn(), checkOutTime);
        long totalSeconds = duration.toSeconds();
        if (totalSeconds < 0) totalSeconds = 0;
        attendance.setWorkingHours(totalSeconds / 3600.0); // full precision, no rounding

        return toDTO(attendanceRepository.save(attendance));
    }

    // ── query methods ───────────────────────────────────────────────────────

    public List<AttendanceDTO> getMyAttendance() {
        Employee employee = getCurrentEmployee();
        return attendanceRepository.findByEmployeeOrderByDateDesc(employee)
            .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<AttendanceDTO> getAllAttendance() {
        return attendanceRepository.findAll()
            .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<AttendanceDTO> getAttendanceByEmployee(Long employeeId) {
        return attendanceRepository.findByEmployeeId(employeeId)
            .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public long countPresentToday() {
        return attendanceRepository.countPresentByDate(LocalDate.now());
    }

    public long countMyAttendance() {
        Employee employee = getCurrentEmployee();
        return attendanceRepository.findByEmployeeOrderByDateDesc(employee).size();
    }

    /**
     * Returns today's attendance DTO for the current employee, or null if none exists.
     */
    public AttendanceDTO getMyTodayAttendance() {
        Employee employee = getCurrentEmployee();
        return attendanceRepository.findByEmployeeAndDate(employee, LocalDate.now())
            .map(this::toDTO)
            .orElse(null);
    }
}

package com.employeetrack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for attendance records returned to the frontend.
 * Carries pre-formatted HH:mm:ss strings for times and duration
 * so the frontend never performs its own time arithmetic.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDTO {

    private Long id;

    // Employee info (flat — avoids serialising full entity graph)
    private Long employeeDbId;
    private String employeeId;     // e.g. "EMP001"
    private String employeeName;
    private String departmentName;

    /** ISO date string: "YYYY-MM-DD" */
    private String date;

    /**
     * Check-in time formatted as "HH:mm:ss".
     * Null / absent if the employee has not checked in.
     */
    private String checkIn;

    /**
     * Check-out time formatted as "HH:mm:ss".
     * Null when the employee has checked in but not yet checked out.
     * We never auto-fill this value; it is only set when the employee
     * explicitly calls the check-out endpoint.
     */
    private String checkOut;

    /**
     * Completed working duration in "HH:mm:ss" format.
     * Null / absent when either timestamp is missing (e.g. not checked out).
     * Computed once at checkout time and stored in the DB as workingHours (Double).
     * This field converts that stored value back to HH:mm:ss for display.
     */
    private String workingDuration;

    /** Raw decimal hours kept for backward-compatibility with existing callers. */
    private Double workingHours;

    /** PRESENT | ABSENT | HALF_DAY | LEAVE */
    private String status;
}

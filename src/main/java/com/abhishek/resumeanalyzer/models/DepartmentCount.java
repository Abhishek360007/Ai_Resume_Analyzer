package com.abhishek.resumeanalyzer.models;

/**
 * Count of employees per department (used in EmployeeStatistics)
 */
public record DepartmentCount(
        String department,
        long count
) {}

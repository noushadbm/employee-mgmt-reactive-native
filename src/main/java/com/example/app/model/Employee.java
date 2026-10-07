package com.example.app.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("employee")
public record Employee(
        @Id Long id,
        String firstName,
        String lastName,
        String email,
        String department,
        String jobTitle,
        BigDecimal salary,
        LocalDate hireDate) {

    public Employee withId(Long newId) {
        return new Employee(newId, firstName, lastName, email, department, jobTitle, salary, hireDate);
    }
}

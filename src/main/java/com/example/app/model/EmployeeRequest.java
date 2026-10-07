package com.example.app.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 100) String department,
        @Size(max = 100) String jobTitle,
        @DecimalMin("0") BigDecimal salary,
        LocalDate hireDate) {

    public Employee toEmployee(Long id) {
        return new Employee(id, firstName, lastName, email, department, jobTitle, salary, hireDate);
    }
}

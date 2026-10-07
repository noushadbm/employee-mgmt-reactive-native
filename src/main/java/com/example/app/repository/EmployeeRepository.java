package com.example.app.repository;

import com.example.app.model.Employee;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EmployeeRepository extends ReactiveCrudRepository<Employee, Long> {
    Flux<Employee> findByDepartment(String department);

    Mono<Boolean> existsByEmail(String email);

    Mono<Boolean> existsByEmailAndIdNot(String email, Long id);
}

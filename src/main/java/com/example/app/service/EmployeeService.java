package com.example.app.service;

import com.example.app.model.Employee;
import com.example.app.model.EmployeeRequest;
import com.example.app.repository.EmployeeRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class EmployeeService {

    private final EmployeeRepository repository;

    public EmployeeService(EmployeeRepository repository) {
        this.repository = repository;
    }

    public Mono<Employee> create(EmployeeRequest request) {
        return repository.existsByEmail(request.email())
                .flatMap(exists -> exists
                        ? Mono.<Employee>error(new DuplicateEmailException(request.email()))
                        : repository.save(request.toEmployee(null)))
                .onErrorMap(DuplicateKeyException.class, e -> new DuplicateEmailException(request.email()));
    }

    public Flux<Employee> list(String department) {
        return department == null || department.isBlank()
                ? repository.findAll()
                : repository.findByDepartment(department);
    }

    public Mono<Employee> get(Long id) {
        return repository.findById(id).switchIfEmpty(Mono.error(new EmployeeNotFoundException(id)));
    }

    public Mono<Employee> update(Long id, EmployeeRequest request) {
        return get(id)
                .then(repository.existsByEmailAndIdNot(request.email(), id))
                .flatMap(exists -> exists
                        ? Mono.<Employee>error(new DuplicateEmailException(request.email()))
                        : repository.save(request.toEmployee(id)))
                .onErrorMap(DuplicateKeyException.class, e -> new DuplicateEmailException(request.email()));
    }

    public Mono<Void> delete(Long id) {
        return get(id).flatMap(repository::delete);
    }
}

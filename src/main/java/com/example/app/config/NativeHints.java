package com.example.app.config;

import com.example.app.model.Employee;
import com.example.app.model.EmployeeRequest;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/** Native-image hints: keep schema.sql and reflect on the model classes. */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeHints.Hints.class)
public class NativeHints {

    static class Hints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.resources().registerPattern("schema.sql");
            hints.reflection().registerType(Employee.class, MemberCategory.values());
            hints.reflection().registerType(EmployeeRequest.class, MemberCategory.values());
        }
    }
}

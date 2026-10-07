CREATE TABLE IF NOT EXISTS employee (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name  VARCHAR(100)  NOT NULL,
    last_name   VARCHAR(100)  NOT NULL,
    email       VARCHAR(255)  NOT NULL,
    department  VARCHAR(100),
    job_title   VARCHAR(100),
    salary      DECIMAL(15,2),
    hire_date   DATE,
    CONSTRAINT uk_employee_email UNIQUE (email)
);

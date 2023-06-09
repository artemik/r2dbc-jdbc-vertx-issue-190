package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;

// Disable Hibernate to make sure it doesn't touch our DataSource.
@SpringBootApplication(exclude = HibernateJpaAutoConfiguration.class)
@Import(DataSourceConfiguration.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

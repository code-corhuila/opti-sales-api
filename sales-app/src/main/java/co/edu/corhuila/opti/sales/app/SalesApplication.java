package co.edu.corhuila.opti.sales.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the sales service. */
@SpringBootApplication(scanBasePackages = "co.edu.corhuila.opti.sales")
public class SalesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalesApplication.class, args);
    }
}

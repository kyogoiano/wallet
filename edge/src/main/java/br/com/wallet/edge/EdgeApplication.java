package br.com.wallet.edge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Independent Spring Boot entry point for the Wallet Edge Gateway (REQ-PRC-001).
 * Operates as a stateless perimeter gateway with zero relational database connections (I-STATE-001, REQ-PRC-003).
 */
@SpringBootApplication(
        scanBasePackages = {"br.com.wallet.edge", "br.com.wallet.core"}
)
public class EdgeApplication {

    static void main(String[] args) {
        SpringApplication.run(EdgeApplication.class, args);
    }
}

package br.com.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@Modulithic(
		systemName = "Wallet Service",
		sharedModules = {"core"}
)
@SpringBootApplication
public class WalletApplication {

	static void main(String[] args) {
		SpringApplication.run(WalletApplication.class, args);
	}

}

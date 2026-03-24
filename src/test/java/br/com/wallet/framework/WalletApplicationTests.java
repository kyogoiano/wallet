package br.com.wallet.framework;

import br.com.wallet.WalletApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = WalletApplication.class)
class WalletApplicationTests {

	@Test
	void contextLoads() {
	}

}

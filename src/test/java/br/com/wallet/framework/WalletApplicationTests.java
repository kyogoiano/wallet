package br.com.wallet.framework;

import br.com.wallet.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(IntegrationTestBase.class)
class WalletApplicationTests {

	@Test
	void contextLoads() {
	}

}

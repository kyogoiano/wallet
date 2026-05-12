package br.com.wallet.framework;

import br.com.wallet.support.IntegrationTestBase;
import br.com.wallet.support.DockerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import({IntegrationTestBase.class})
@ActiveProfiles("test")
class WalletApplicationTests extends DockerProperties {

	@Test
	void contextLoads() {
	}

}

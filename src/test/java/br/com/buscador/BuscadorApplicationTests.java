package br.com.buscador;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "buscador.catalog.refresh.enabled=false")
class BuscadorApplicationTests {

	@Test
	void contextLoads() {
	}

}

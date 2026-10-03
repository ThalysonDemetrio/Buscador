package br.com.buscador;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.UUID;

@SpringBootTest(properties = "buscador.catalog.refresh.enabled=false")
class BuscadorApplicationTests {

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		Path db = Path.of(System.getProperty("java.io.tmpdir"),
				"buscador-app-" + UUID.randomUUID() + ".db");
		registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + db);
	}

	@Test
	void contextLoads() {
	}

}

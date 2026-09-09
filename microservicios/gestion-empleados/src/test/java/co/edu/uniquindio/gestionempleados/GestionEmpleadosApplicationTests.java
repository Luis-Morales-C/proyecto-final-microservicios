package co.edu.uniquindio.gestionempleados;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
		properties = {
				"DEPARTAMENTOS_URL=http://localhost:8000",
				"spring.datasource.url=jdbc:h2:mem:testdb",
				"spring.datasource.driver-class-name=org.h2.Driver",
				"spring.datasource.username=sa",
				"spring.datasource.password=",
				"spring.jpa.hibernate.ddl-auto=create-drop"
		}
)
class GestionEmpleadosApplicationTests {

	@Test
	void contextLoads() {
	}
}
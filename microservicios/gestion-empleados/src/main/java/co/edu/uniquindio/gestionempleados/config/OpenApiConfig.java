package co.edu.uniquindio.gestionempleados.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI gestionEmpleadosOpenAPI() {

        return new OpenAPI()
                .info(
                        new Info()
                                .title(
                                        "Microservicio "
                                                + "de Gestión de Empleados"
                                )
                                .version("1.0.0")
                                .description(
                                        "API REST para registrar y "
                                                + "consultar empleados. "
                                                + "La creación valida la "
                                                + "existencia del departamento "
                                                + "mediante comunicación HTTP "
                                                + "con el microservicio de "
                                                + "departamentos."
                                )
                                .contact(
                                        new Contact()
                                                .name(
                                                        "Equipo "
                                                                + "del proyecto"
                                                )
                                )
                );
    }
}
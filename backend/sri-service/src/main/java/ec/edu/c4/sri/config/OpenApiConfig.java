package ec.edu.c4.sri.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI sriOpenApi() {
        return new OpenAPI().info(new Info()
                .title("SRI Service - Contribuyentes y vehículos")
                .version("1.0.0")
                .description("Verifica que un RUC sea contribuyente PERSONA NATURAL del SRI y consulta "
                        + "la información de un vehículo por placa.")
                .license(new License().name("Repositorio")
                        .url("https://github.com/mateo-cuascota/Arquitectura_C4_Mateo_Cuascota")));
    }
}

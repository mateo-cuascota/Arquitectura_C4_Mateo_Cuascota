package ec.edu.c4.ant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI antOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ANT Service - Puntos de licencia")
                .version("1.0.0")
                .description("Consulta los puntos de la licencia en la ANT con el patrón Cache-Aside + "
                        + "stale-if-error + Retry + Circuit Breaker sobre Redis en la nube.")
                .license(new License().name("Repositorio")
                        .url("https://github.com/mateo-cuascota/Arquitectura_C4_Mateo_Cuascota")));
    }
}

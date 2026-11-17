package ec.edu.c4.ant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AntServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AntServiceApplication.class, args);
    }
}

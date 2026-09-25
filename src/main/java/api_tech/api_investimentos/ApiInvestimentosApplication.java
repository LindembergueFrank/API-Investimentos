package api_tech.api_investimentos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApiInvestimentosApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiInvestimentosApplication.class, args);
	}

}

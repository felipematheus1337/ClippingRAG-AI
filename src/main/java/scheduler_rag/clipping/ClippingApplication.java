package scheduler_rag.clipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClippingApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClippingApplication.class, args);
	}

}

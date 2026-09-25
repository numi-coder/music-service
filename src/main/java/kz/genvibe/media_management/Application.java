package kz.genvibe.media_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class Application {

	// Jingle start/end dates are wall-clock times, so they are read in this zone.
	// Override with the APP_TIME_ZONE environment variable.
	private static final String DEFAULT_TIME_ZONE = "Asia/Singapore";

	static void main(String[] args) {
		var zone = System.getenv().getOrDefault("APP_TIME_ZONE", DEFAULT_TIME_ZONE);
		TimeZone.setDefault(TimeZone.getTimeZone(zone));
		SpringApplication.run(Application.class, args);
	}

}

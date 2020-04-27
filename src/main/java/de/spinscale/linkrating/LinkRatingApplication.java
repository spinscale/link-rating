package de.spinscale.linkrating;

import co.elastic.apm.attach.ElasticApmAttacher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LinkRatingApplication {

	public static void main(String[] args) {
		ElasticApmAttacher.attach();
		SpringApplication.run(LinkRatingApplication.class, args);
	}
}

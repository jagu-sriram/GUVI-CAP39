package com.sriram.cap39;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.sriram.cap39.config.DatasetConfig;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Cap39BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(
				Cap39BackendApplication.class, args
		);
	}


	@Bean
	CommandLineRunner validateDatasets() {
		return args -> {
			System.out.println("========== DATASET CONFIGURATION ==========");

			DatasetConfig.getAll().forEach((name, config) -> {
				System.out.println(
						name + " | " +
								config.primaryKey() + " | " +
								config.columns().size() + " columns"
				);
			});

			System.out.println(
					"TOTAL DATASETS: " + DatasetConfig.getAll().size()
			);
		};
	}
}
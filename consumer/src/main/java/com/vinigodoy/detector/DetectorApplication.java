package com.vinigodoy.detector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(proxyBeanMethods = false)
@ConfigurationPropertiesScan
@EnableScheduling
public final class DetectorApplication {

  public static void main(String[] args) {
    SpringApplication.run(DetectorApplication.class, args);
  }
}

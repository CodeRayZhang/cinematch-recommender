package com.cinematch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class CineMatchApplication {
  public static void main(String[] args) {
    SpringApplication.run(CineMatchApplication.class, args);
  }
}

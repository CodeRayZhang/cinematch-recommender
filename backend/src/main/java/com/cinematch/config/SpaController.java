package com.cinematch.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
  @GetMapping({
    "/",
    "/picks",
    "/library",
    "/admin",
    "/movie/{id}",
    "/sources",
    "/profile",
    "/collections",
    "/collections/{id}"
  })
  public String app() {
    return "forward:/index.html";
  }
}

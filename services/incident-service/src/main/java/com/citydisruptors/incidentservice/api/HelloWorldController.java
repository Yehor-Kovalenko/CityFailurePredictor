package com.citydisruptors.incidentservice.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloWorldController {

    @GetMapping("/incident")
    public String hello(){
        return "Hello";
    }
}

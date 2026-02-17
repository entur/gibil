package org.gibil

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["org.gibil", "routes.api", "config", "siri", "handler", "service", "subscription", "model"])
@EnableScheduling
class Application

fun main(args: Array<String>) {
    //Output can be seen on localhost:8080/siri
    runApplication<Application>(*args)
}

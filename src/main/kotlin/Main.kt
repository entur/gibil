package org.gibil

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["org.gibil", "routes.api", "config", "siri", "handler", "service"])
class Application

fun main(args: Array<String>) {
    //Output can be seen on localhost:8080/siri
    runApplication<Application>(*args)
}

package com.example.userapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RequestPredicates.version;
import static org.springframework.web.servlet.function.RouterFunctions.route;

@Configuration(proxyBeanMethods = false)
public class UserRoutes {

    @Bean
    RouterFunction<ServerResponse> userApiRoutes() {
        return route()
                .GET("/api/users/{id}", version("1.0"), this::findByIdV1)
                .GET("/api/users/{id}", version("2.0"), this::findByIdV2)
                .build();
    }

    private ServerResponse findByIdV1(ServerRequest request) {
        long id = Long.parseLong(request.pathVariable("id"));
        if (id != 1L && id != 2L) {
            return ServerResponse.notFound().build();
        }
        return ServerResponse.ok().body(id == 1L
                ? new UserV1(1L, "Grace Hopper", "grace@example.com")
                : new UserV1(2L, "Alan Turing", "alan@example.com"));
    }

    private ServerResponse findByIdV2(ServerRequest request) {
        long id = Long.parseLong(request.pathVariable("id"));
        if (id != 1L && id != 2L) {
            return ServerResponse.notFound().build();
        }
        return ServerResponse.ok().body(id == 1L
                ? new UserV2(1L, "Grace", "Hopper", "grace@example.com")
                : new UserV2(2L, "Alan", "Turing", "alan@example.com"));
    }
}

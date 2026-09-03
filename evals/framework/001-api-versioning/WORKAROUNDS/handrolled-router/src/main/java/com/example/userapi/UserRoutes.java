package com.example.userapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RouterFunctions.route;

/**
 * One route for both shapes, with the version read out of the request by a
 * helper method named version. No framework versioning anywhere.
 */
@Configuration(proxyBeanMethods = false)
public class UserRoutes {

    @Bean
    RouterFunction<ServerResponse> userApiRoutes() {
        return route(GET("/api/users/{id}"), this::findById);
    }

    private ServerResponse findById(ServerRequest request) {
        long id = Long.parseLong(request.pathVariable("id"));
        if (id != 1L && id != 2L) {
            return ServerResponse.notFound().build();
        }
        if ("2.0".equals(version(request))) {
            return ServerResponse.ok().body(id == 1L
                    ? new UserV2(1L, "Grace", "Hopper", "grace@example.com")
                    : new UserV2(2L, "Alan", "Turing", "alan@example.com"));
        }
        return ServerResponse.ok().body(id == 1L
                ? new UserV1(1L, "Grace Hopper", "grace@example.com")
                : new UserV1(2L, "Alan Turing", "alan@example.com"));
    }

    private String version(ServerRequest request) {
        return request.headers().firstHeader("X-API-Version");
    }
}

package com.example;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.sleuth.Sampler;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

@EnableDiscoveryClient
@EnableBinding(Source.class)
@EnableCircuitBreaker
@SpringBootApplication
public class ReservationClientApplication {
    
    @Bean
    Sampler<?> sampler (){
        return new AlwaysSampler();
    }

    public static void main(String[] args) {
        SpringApplication.run(ReservationClientApplication.class, args);
    }
}

@RestController
@RequestMapping("/reservations")
class ReservationApiGatewayRestController {

    @Autowired
    @LoadBalanced
    private RestTemplate restTemplate;

    @Autowired
    @Output("output")
    private MessageChannel messageChannel;

    public Collection<String> getReservationNamesFallback() {
        return Collections.emptyList();
    }

    @RequestMapping(method = RequestMethod.POST)
    public void acceptNewReservations(@RequestBody Reservation r) {
        Message<String> build = MessageBuilder.withPayload(r.getReservationName()).build();
        this.messageChannel.send(build);
    }

    @RequestMapping("/names")
    @HystrixCommand(fallbackMethod = "getReservationNamesFallback")
    public Collection<String> getReservationNames() {

        ParameterizedTypeReference<Resources<Reservation>> ptr =
                new ParameterizedTypeReference<Resources<Reservation>>() {
                };

        return this.restTemplate.exchange("http://reservation-service/reservations", HttpMethod.GET, null, ptr)
                .getBody()
                .getContent()
                .stream()
                .map(Reservation::getReservationName)
                .collect(Collectors.toList());
    }

}

class Reservation {

    private String reservationName;

    public String getReservationName() {
        return reservationName;
    }
}
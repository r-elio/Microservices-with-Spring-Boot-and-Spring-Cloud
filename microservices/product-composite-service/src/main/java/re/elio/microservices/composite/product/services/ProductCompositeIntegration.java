package re.elio.microservices.composite.product.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import re.elio.api.core.product.Product;
import re.elio.api.core.product.ProductService;
import re.elio.api.core.recommendation.Recommendation;
import re.elio.api.core.recommendation.RecommendationService;
import re.elio.api.core.review.Review;
import re.elio.api.core.review.ReviewService;
import re.elio.api.event.Event;
import re.elio.api.exceptions.InvalidInputException;
import re.elio.api.exceptions.NotFoundException;
import re.elio.util.http.HttpErrorInfo;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;

import static reactor.core.publisher.Flux.empty;

@Component
public class ProductCompositeIntegration implements ProductService, RecommendationService, ReviewService {
    private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeIntegration.class);
    private final ObjectMapper objectMapper;
    private final String productServiceURL;
    private final String recommendationServiceURL;
    private final String reviewServiceURL;

    private final WebClient webClient;
    private final StreamBridge streamBridge;
    private final Scheduler publishEventScheduler;

    public ProductCompositeIntegration(ObjectMapper objectMapper,
                                       @Value("${app.product-service.host}") String productServiceHost,
                                       @Value("${app.product-service.port}") int productServicePort,
                                       @Value("${app.recommendation-service.host}") String recommendationServiceHost,
                                       @Value("${app.recommendation-service.port}") int recommendationServicePort,
                                       @Value("${app.review-service.host}") String reviewServiceHost,
                                       @Value("${app.review-service.port}") int reviewServicePort,
                                       WebClient.Builder webClient,
                                       StreamBridge streamBridge,
                                       @Qualifier("publishEventScheduler") Scheduler publishEventScheduler) {
        this.objectMapper = objectMapper;
        this.webClient = webClient.build();
        this.streamBridge = streamBridge;
        this.publishEventScheduler = publishEventScheduler;

        this.productServiceURL = String.format("http://%s:%s", productServiceHost, productServicePort);
        this.recommendationServiceURL = String.format("http://%s:%s", recommendationServiceHost, recommendationServicePort);
        this.reviewServiceURL = String.format("http://%s:%s", reviewServiceHost, reviewServicePort);
    }

    @Override
    public Mono<Product> getProduct(int productId) {
        String url = productServiceURL + "/product/" + productId;
        LOG.debug("Will call getProduct API on URL: {}", url);
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Product.class)
                .log(LOG.getName(), Level.FINE)
                .onErrorMap(WebClientResponseException.class, this::handleException);
    }

    @Override
    public Mono<Product> createProduct(Product body) {
        return Mono.fromCallable(() -> {
            sendMessage("products-out-0", new Event<>(Event.Type.CREATE, body.getProductId(), body));
            return body;
        }).subscribeOn(publishEventScheduler);
    }

    @Override
    public Mono<Void> deleteProduct(int productId) {
        return Mono.fromRunnable(() -> sendMessage("products-out-0", new Event<>(Event.Type.DELETE, productId, null)))
                .subscribeOn(publishEventScheduler).then();
    }

    @Override
    public Flux<Recommendation> getRecommendations(int productId) {
        String url = String.format("%s/recommendation?productId=%s", recommendationServiceURL, productId);
        LOG.debug("Will call getRecommendations API on URL: {}", url);
        // Return an empty result if something goes wrong to make it possible
        // for the composite service to return partial responses
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToFlux(Recommendation.class)
                .log(LOG.getName(), Level.FINE)
                .onErrorResume(error -> empty());
    }

    @Override
    public Mono<Recommendation> createRecommendation(Recommendation body) {
        return Mono.fromCallable(() -> {
            sendMessage("recommendations-out-0", new Event<>(Event.Type.CREATE, body.getProductId(), body));
            return body;
        }).subscribeOn(publishEventScheduler);
    }

    @Override
    public Mono<Void> deleteRecommendations(int productId) {
        return Mono.fromRunnable(() -> sendMessage("recommendations-out-0", new Event<>(Event.Type.DELETE, productId, null)))
                .subscribeOn(publishEventScheduler).then();
    }

    @Override
    public Flux<Review> getReviews(int productId) {
        String url = String.format("%s/review?productId=%s", reviewServiceURL, productId);
        LOG.debug("Will call getReviews API on URL: {}", url);
        // Return an empty result if something goes wrong to make it possible
        // for the composite service to return partial responses
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToFlux(Review.class)
                .log(LOG.getName(), Level.FINE)
                .onErrorResume(error -> empty());
    }

    @Override
    public Mono<Review> createReview(Review body) {
        return Mono.fromCallable(() -> {
            sendMessage("reviews-out-0", new Event<>(Event.Type.CREATE, body.getProductId(), body));
            return body;
        }).subscribeOn(publishEventScheduler);
    }

    @Override
    public Mono<Void> deleteReviews(int productId) {
        return Mono.fromRunnable(() -> sendMessage("reviews-out-0", new Event<>(Event.Type.DELETE, productId, null)))
                .subscribeOn(publishEventScheduler).then();
    }

    public Mono<Health> getProductHealth() {
        return getHealth(productServiceURL);
    }

    public Mono<Health> getRecommendationHealth() {
        return getHealth(recommendationServiceURL);
    }

    public Mono<Health> getReviewHealth() {
        return getHealth(reviewServiceURL);
    }

    private Mono<Health> getHealth(String url) {
        url += "/actuator/health";
        LOG.debug("Will call the Health API on URL: {}", url);
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .map(s -> new Health.Builder().up().build())
                .onErrorResume(ex -> Mono.just(new Health.Builder().down(ex).build()))
                .log(LOG.getName(), Level.FINE);
    }

    private void sendMessage(String bindingName, Event event) {
        LOG.debug("Sending a {} message to {}", event.getEventType(), bindingName);
        Message<Event> message = MessageBuilder.withPayload(event)
                .setHeader("partitionKey", event.getKey())
                .build();
        streamBridge.send(bindingName, message);
    }

    private Throwable handleException(Throwable ex) {
        if (!(ex instanceof WebClientResponseException wcEx)) {
            LOG.warn("Got an unexpected error: {}, will rethrow it.", ex.toString());
            return ex;
        }
        switch (Objects.requireNonNull(HttpStatus.resolve(wcEx.getStatusCode().value()))) {
            case NOT_FOUND:
                return new NotFoundException(getErrorMessage(wcEx));
            case UNPROCESSABLE_ENTITY:
                return new InvalidInputException(getErrorMessage(wcEx));
            default:
                LOG.warn("Got an unexpected HTTP error: {}, will rethrow it.", wcEx.getStatusCode());
                LOG.warn("Error body: {}", wcEx.getResponseBodyAsString());
                return ex;
        }
    }

    private String getErrorMessage(WebClientResponseException ex) {
        try {
            return objectMapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
        } catch (IOException ioEx) {
            return ex.getMessage();
        }
    }
}

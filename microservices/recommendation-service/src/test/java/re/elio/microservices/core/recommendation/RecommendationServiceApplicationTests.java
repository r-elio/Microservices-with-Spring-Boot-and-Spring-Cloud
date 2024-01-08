package re.elio.microservices.core.recommendation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import re.elio.api.core.recommendation.Recommendation;
import re.elio.api.event.Event;
import re.elio.api.exceptions.InvalidInputException;
import re.elio.microservices.core.recommendation.persistence.RecommendationRepository;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static reactor.core.publisher.Mono.just;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class RecommendationServiceApplicationTests extends MongoDbTestBase {
    @Autowired
    private WebTestClient client;
    @Autowired
    private RecommendationRepository repository;

    @Autowired
    @Qualifier("messageProcessor")
    private Consumer<Event<Integer, Recommendation>> messageProcessor;

    @Test
    void contextLoads() {
    }

    @BeforeEach
    void setupDb() {
        repository.deleteAll().block();
    }

    @Test
    void getRecommendationsByProductId() {

        int productId = 1;

        postAndVerifyRecommendation(productId, 1, OK);
        postAndVerifyRecommendation(productId, 2, OK);
        postAndVerifyRecommendation(productId, 3, OK);

        assertEquals(3, (long) repository.findByProductId(productId).count().block());

        getAndVerifyRecommendationsByProductId(productId, OK)
                .jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[2].productId").isEqualTo(productId)
                .jsonPath("$[2].recommendationId").isEqualTo(3);
    }

    @Test
    void duplicateError() {

        int productId = 1;
        int recommendationId = 1;

        sendCreateRecommendationEvent(productId, recommendationId);

        assertEquals(1, (long) repository.count().block());

        InvalidInputException thrown = assertThrows(InvalidInputException.class, () -> sendCreateRecommendationEvent(productId, recommendationId), "Expected an InvalidInputException!");
        assertEquals("Duplicate key, Product ID: 1, Recommendation ID: 1", thrown.getMessage());

        assertEquals(1, (long) repository.count().block());
    }

    @Test
    void deleteRecommendations() {

        int productId = 1;
        int recommendationId = 1;

        sendCreateRecommendationEvent(productId, recommendationId);
        assertEquals(1, (long) repository.findByProductId(productId).count().block());

        sendDeleteRecommendationEvent(productId);
        assertEquals(0, (long) repository.findByProductId(productId).count().block());

        sendDeleteRecommendationEvent(productId);
    }

    @Test
    void getRecommendationsMissingParameter() {
        getAndVerifyRecommendationsByProductId("", BAD_REQUEST)
                .jsonPath("$.path").isEqualTo("/recommendation")
                .jsonPath("$.message").isEqualTo("Required query parameter 'productId' is not present.");
    }

    @Test
    void getRecommendationsInvalidParameter() {
        getAndVerifyRecommendationsByProductId("?productId=no-integer", BAD_REQUEST)
                .jsonPath("$.path").isEqualTo("/recommendation")
                .jsonPath("$.message").isEqualTo("Type mismatch.");
    }

    @Test
    void getRecommendationsNotFound() {
        getAndVerifyRecommendationsByProductId("?productId=113", OK)
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void getRecommendationsInvalidParameterNegativeValue() {
        int productIdInvalid = -1;
        getAndVerifyRecommendationsByProductId("?productId=" + productIdInvalid, UNPROCESSABLE_ENTITY)
                .jsonPath("$.path").isEqualTo("/recommendation")
                .jsonPath("$.message").isEqualTo("Invalid productId: " + productIdInvalid);
    }

    private WebTestClient.BodyContentSpec getAndVerifyRecommendationsByProductId(int productId, HttpStatus expectedStatus) {
        return getAndVerifyRecommendationsByProductId("?productId=" + productId, expectedStatus);
    }

    private WebTestClient.BodyContentSpec getAndVerifyRecommendationsByProductId(String productIdQuery, HttpStatus expectedStatus) {
        return client.get()
                .uri("/recommendation" + productIdQuery)
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectHeader().contentType(APPLICATION_JSON)
                .expectBody();
    }

    private WebTestClient.BodyContentSpec postAndVerifyRecommendation(int productId, int recommendationId, HttpStatus expectedStatus) {
        Recommendation recommendation = new Recommendation(productId, recommendationId, "Author " + recommendationId, recommendationId, "Content " + recommendationId, "SA");
        return client.post()
                .uri("/recommendation")
                .body(just(recommendation), Recommendation.class)
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectHeader().contentType(APPLICATION_JSON)
                .expectBody();
    }

    private WebTestClient.BodyContentSpec deleteAndVerifyRecommendationsByProductId(int productId, HttpStatus expectedStatus) {
        return client.delete()
                .uri("/recommendation?productId=" + productId)
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody();
    }

    private void sendCreateRecommendationEvent(int productId, int recommendationId) {
        Recommendation recommendation = new Recommendation(productId, recommendationId, "Author " + recommendationId, recommendationId, "Content " + recommendationId, "SA");
        Event<Integer, Recommendation> event = new Event<>(Event.Type.CREATE, productId, recommendation);
        messageProcessor.accept(event);
    }

    private void sendDeleteRecommendationEvent(int productId) {
        Event<Integer, Recommendation> event = new Event<>(Event.Type.DELETE, productId, null);
        messageProcessor.accept(event);
    }
}

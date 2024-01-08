package re.elio.microservices.core.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import re.elio.api.core.product.Product;
import re.elio.api.event.Event;
import re.elio.api.exceptions.InvalidInputException;
import re.elio.microservices.core.product.persistence.ProductRepository;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static reactor.core.publisher.Mono.just;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class ProductServiceApplicationTests extends MongoDbTestBase {
    @Autowired
    private WebTestClient client;
    @Autowired
    private ProductRepository repository;

    @Autowired
    @Qualifier("messageProcessor")
    private Consumer<Event<Integer, Product>> messageProcessor;

    @Test
    void contextLoads() {
    }

    @BeforeEach
    void setupDb() {
        repository.deleteAll().block();
    }

    @Test
    void getProductById() {

        int productId = 1;

        assertNull(repository.findByProductId(productId).block());
        assertEquals(0, (long) repository.count().block());

        sendCreateProductEvent(productId);

        assertNotNull(repository.findByProductId(productId).block());
        assertEquals(1, (long) repository.count().block());

        getAndVerifyProduct(productId, OK).jsonPath("$.productId").isEqualTo(productId);
    }

    @Test
    void duplicateError() {

        int productId = 1;

        assertNull(repository.findByProductId(productId).block());

        sendCreateProductEvent(productId);

        assertNotNull(repository.findByProductId(productId).block());

        InvalidInputException thrown = assertThrows(InvalidInputException.class, () -> sendCreateProductEvent(productId), "Expected an InvalidInputException!");
        assertEquals("Duplicate key, Product ID: " + productId, thrown.getMessage());
    }

    @Test
    void deleteProduct() {

        int productId = 1;

        sendCreateProductEvent(productId);
        assertNotNull(repository.findByProductId(productId).block());

        sendDeleteProductEvent(productId);
        assertNull(repository.findByProductId(productId).block());

        sendDeleteProductEvent(productId);
    }

    @Test
    void getProductInvalidParameterString() {
        getAndVerifyProduct("/no-integer", BAD_REQUEST)
                .jsonPath("$.path").isEqualTo("/product/no-integer")
                .jsonPath("$.message").isEqualTo("Type mismatch.");
    }

    @Test
    void getProductNotFound() {
        int productIdNotFound = 13;
        getAndVerifyProduct(productIdNotFound, NOT_FOUND)
                .jsonPath("$.path").isEqualTo("/product/" + productIdNotFound)
                .jsonPath("$.message").isEqualTo("No product found for productId: " + productIdNotFound);
    }

    @Test
    void getProductInvalidParameterNegativeValue() {
        int productIdInvalid = -1;
        getAndVerifyProduct(productIdInvalid, UNPROCESSABLE_ENTITY)
                .jsonPath("$.path").isEqualTo("/product/" + productIdInvalid)
                .jsonPath("$.message").isEqualTo("Invalid productId: " + productIdInvalid);
    }

    private WebTestClient.BodyContentSpec getAndVerifyProduct(int productId, HttpStatus expectedStatus) {
        return getAndVerifyProduct("/" + productId, expectedStatus);
    }

    private WebTestClient.BodyContentSpec getAndVerifyProduct(String productIdPath, HttpStatus expectedStatus) {
        return client.get()
                .uri("/product" + productIdPath)
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectHeader().contentType(APPLICATION_JSON)
                .expectBody();
    }

    private WebTestClient.BodyContentSpec postAndVerifyProduct(int productId, HttpStatus expectedStatus) {
        Product product = new Product(productId, "Name " + productId, productId, "SA");
        return client.post()
                .uri("/product")
                .body(just(product), Product.class)
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectHeader().contentType(APPLICATION_JSON)
                .expectBody();
    }

    private WebTestClient.BodyContentSpec deleteAndVerifyProduct(int productId, HttpStatus expectedStatus) {
        return client.delete()
                .uri("/product/" + productId)
                .accept(APPLICATION_JSON)
                .exchange()
                .expectStatus().isEqualTo(expectedStatus)
                .expectBody();
    }

    private void sendCreateProductEvent(int productId) {
        Product product = new Product(productId, "Name " + productId, productId, "SA");
        Event<Integer, Product> event = new Event<>(Event.Type.CREATE, productId, product);
        messageProcessor.accept(event);
    }

    private void sendDeleteProductEvent(int productId) {
        Event<Integer, Product> event = new Event<>(Event.Type.DELETE, productId, null);
        messageProcessor.accept(event);
    }
}

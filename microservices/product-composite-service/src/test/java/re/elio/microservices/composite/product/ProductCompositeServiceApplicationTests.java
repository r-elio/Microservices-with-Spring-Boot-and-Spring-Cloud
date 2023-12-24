package re.elio.microservices.composite.product;

import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static java.util.Collections.singletonList;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import re.elio.api.core.product.Product;
import re.elio.api.core.recommendation.Recommendation;
import re.elio.api.core.review.Review;
import re.elio.api.exceptions.InvalidInputException;
import re.elio.api.exceptions.NotFoundException;
import re.elio.microservices.composite.product.services.ProductCompositeIntegration;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class ProductCompositeServiceApplicationTests {

	private static final int PRODUCT_ID_OK = 1;
	private static final int PRODUCT_ID_NOT_FOUND = 2;
	private static final int PRODUCT_ID_INVALID = 3;

	@Autowired
	private WebTestClient client;

	@MockBean
	private ProductCompositeIntegration integration;

	@Test
	void contextLoads() {
	}

	@BeforeEach
	void setUp() {
		when(integration.getProduct(PRODUCT_ID_OK)).thenReturn(new Product(PRODUCT_ID_OK, "name",  1, "mock-address"));
		when(integration.getRecommendations(PRODUCT_ID_OK))
			.thenReturn(singletonList(new Recommendation(PRODUCT_ID_OK, 1, "author", 1, "content", "mock adress")));
		when(integration.getReviews(PRODUCT_ID_OK))
			.thenReturn(singletonList(new Review(PRODUCT_ID_OK, 1, "author", "subject", "content", "mock address")));
		
		when(integration.getProduct(PRODUCT_ID_NOT_FOUND))
			.thenThrow(new NotFoundException("NOT FOUND: " + PRODUCT_ID_NOT_FOUND));
		when(integration.getProduct(PRODUCT_ID_INVALID))
			.thenThrow(new InvalidInputException("INVALID: " + PRODUCT_ID_INVALID));
	}

	@Test
	void getProductById() {
		client.get()
			.uri("/product-composite/" + PRODUCT_ID_OK)
			.accept(APPLICATION_JSON)
			.exchange()
			.expectStatus().isOk()
			.expectHeader().contentType(APPLICATION_JSON)
			.expectBody()
				.jsonPath("$.productId").isEqualTo(PRODUCT_ID_OK)
				.jsonPath("$.recommendations.length()").isEqualTo(1)
				.jsonPath("$.reviews.length()").isEqualTo(1);
	}

	@Test
	void getProductNotFound() {
		client.get()
			.uri("/product-composite/" + PRODUCT_ID_NOT_FOUND)
			.accept(APPLICATION_JSON)
			.exchange()
			.expectStatus().isNotFound()
			.expectHeader().contentType(APPLICATION_JSON)
			.expectBody()
				.jsonPath("$.path").isEqualTo("/product-composite/" + PRODUCT_ID_NOT_FOUND)
				.jsonPath("$.message").isEqualTo("NOT FOUND: " + PRODUCT_ID_NOT_FOUND);
	}

	@Test
	void getProductInvalidInput() {
		client.get()
			.uri("/product-composite/" + PRODUCT_ID_INVALID)
			.accept(APPLICATION_JSON)
			.exchange()
			.expectStatus().isEqualTo(UNPROCESSABLE_ENTITY)
			.expectHeader().contentType(APPLICATION_JSON)
			.expectBody()
				.jsonPath("$.path").isEqualTo("/product-composite/" + PRODUCT_ID_INVALID)
				.jsonPath("$.message").isEqualTo("INVALID: " + PRODUCT_ID_INVALID);
	}
}

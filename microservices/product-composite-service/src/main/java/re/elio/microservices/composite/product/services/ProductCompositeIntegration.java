package re.elio.microservices.composite.product.services;

import static org.springframework.http.HttpMethod.GET;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import re.elio.api.core.product.Product;
import re.elio.api.core.recommendation.Recommendation;
import re.elio.api.core.review.Review;
import re.elio.api.exceptions.InvalidInputException;
import re.elio.api.exceptions.NotFoundException;
import re.elio.util.http.HttpErrorInfo;

@Component
public class ProductCompositeIntegration {
    private static final Logger LOG = LoggerFactory.getLogger(ProductCompositeIntegration.class);
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String productServiceURL;
    private final String recommendationServiceURL;
    private final String reviewServiceURL;

    public ProductCompositeIntegration(RestTemplate restTemplate, ObjectMapper objectMapper,
            @Value("${app.product-service.host}") String productServiceHost,
            @Value("${app.product-service.port}") int productServicePort,
            @Value("${app.recommendation-service.host}") String recommendationServiceHost,
            @Value("${app.recommendation-service.port}") int recommendationServicePort,
            @Value("${app.review-service.host}") String reviewServiceHost,
            @Value("${app.review-service.port}") int reviewServicePort) {

        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;

        this.productServiceURL = String.format("http://%s:%s/product/", productServiceHost, productServicePort);
        this.recommendationServiceURL = String.format("http://%s:%s/recommendation?productId=",
                recommendationServiceHost, recommendationServicePort);
        this.reviewServiceURL = String.format("http://%s:%s/review?productId=", reviewServiceHost, reviewServicePort);
    }

    public Product getProduct(int productId) {
        try {
            String url = productServiceURL + productId;
            LOG.debug("Will call getProduct API on URL: {}", url);

            Product product = restTemplate.getForObject(url, Product.class);
            LOG.debug("Found a product with id: {}", product.getProductId());

            return product;

        } catch (HttpClientErrorException e) {
            switch (HttpStatus.resolve(e.getStatusCode().value())) {
                case NOT_FOUND:
                    throw new NotFoundException(getErrorMessage(e));
                case UNPROCESSABLE_ENTITY:
                    throw new InvalidInputException(getErrorMessage(e));
                default:
                    LOG.warn("Got an unexpected HTTP error: {}, will rethrow it", e.getStatusCode());
                    LOG.warn("Error body: {}", e.getResponseBodyAsString());
                    throw e;
            }
        }
    }

    private String getErrorMessage(HttpClientErrorException ex) {
        try {
            return objectMapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    public List<Recommendation> getRecommendations(int productId) {
        try {
            String url = recommendationServiceURL + productId;
            LOG.debug("Will call getRecommendations API on URL: {}", url);

            List<Recommendation> recommendations = restTemplate
                    .exchange(url, GET, null, new ParameterizedTypeReference<List<Recommendation>>() {
                    }).getBody();
            LOG.debug("Found {} recommendations for a product with id: {}", recommendations.size(), productId);

            return recommendations;

        } catch (Exception e) {
            LOG.warn("Got an exception while requesting recommendations, return zero recommendations: {}",
                    e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Review> getReviews(int productId) {
        try {
            String url = reviewServiceURL + productId;
            LOG.debug("Will call getReviews API on URL: {}", url);

            List<Review> reviews = restTemplate
                    .exchange(url, GET, null, new ParameterizedTypeReference<List<Review>>() {
                    }).getBody();
            LOG.debug("Found {} reviews for a product with id: {}", reviews.size(), productId);

            return reviews;

        } catch (Exception e) {
            LOG.warn("Got an exception while requesting reviews, return zero reviews: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}

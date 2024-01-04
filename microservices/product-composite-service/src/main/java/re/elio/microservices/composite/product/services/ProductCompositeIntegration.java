package re.elio.microservices.composite.product.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import re.elio.api.core.product.Product;
import re.elio.api.core.product.ProductService;
import re.elio.api.core.recommendation.Recommendation;
import re.elio.api.core.recommendation.RecommendationService;
import re.elio.api.core.review.Review;
import re.elio.api.core.review.ReviewService;
import re.elio.api.exceptions.InvalidInputException;
import re.elio.api.exceptions.NotFoundException;
import re.elio.util.http.HttpErrorInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpMethod.GET;

@Component
public class ProductCompositeIntegration implements ProductService, RecommendationService, ReviewService {
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

        this.productServiceURL = String.format("http://%s:%s/product", productServiceHost, productServicePort);
        this.recommendationServiceURL = String.format("http://%s:%s/recommendation", recommendationServiceHost, recommendationServicePort);
        this.reviewServiceURL = String.format("http://%s:%s/review", reviewServiceHost, reviewServicePort);
    }

    @Override
    public Product getProduct(int productId) {
        try {
            String url = productServiceURL + "/" + productId;
            LOG.debug("Will call getProduct API on URL: {}", url);

            Product product = restTemplate.getForObject(url, Product.class);
            LOG.debug("Found a product with id: {}", product.getProductId());

            return product;

        } catch (HttpClientErrorException ex) {
            throw handleHttpClientException(ex);
        }
    }

    @Override
    public Product createProduct(Product body) {
        try {
            String url = productServiceURL;
            LOG.debug("Will post a new product to URL: {}", url);

            Product product = restTemplate.postForObject(url, body, Product.class);
            LOG.debug("Created a product with id: {}", product.getProductId());

            return product;

        } catch (HttpClientErrorException ex) {
            throw handleHttpClientException(ex);
        }
    }

    @Override
    public void deleteProduct(int productId) {
        try {
            String url = productServiceURL + "/" + productId;
            LOG.debug("Will call the deleteProduct API on URL: {}", url);

            restTemplate.delete(url);

        } catch (HttpClientErrorException ex) {
            throw handleHttpClientException(ex);
        }
    }

    @Override
    public List<Recommendation> getRecommendations(int productId) {
        try {
            String url = String.format("%s?productId=%s", recommendationServiceURL, productId);
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

    @Override
    public Recommendation createRecommendation(Recommendation body) {
        try {
            String url = recommendationServiceURL;
            LOG.debug("Will post a new recommendation to URL: {}", url);

            Recommendation recommendation = restTemplate.postForObject(url, body, Recommendation.class);
            LOG.debug("Created a recommendation with id: {}", recommendation.getProductId());

            return recommendation;

        } catch (HttpClientErrorException ex) {
            throw handleHttpClientException(ex);
        }
    }

    @Override
    public void deleteRecommendations(int productId) {
        try {
            String url = String.format("%s?productId=%s", recommendationServiceURL, productId);
            LOG.debug("Will call the deleteRecommendations API on URL: {}", url);

            restTemplate.delete(url);

        } catch (HttpClientErrorException ex) {
            throw handleHttpClientException(ex);
        }
    }

    @Override
    public List<Review> getReviews(int productId) {
        try {
            String url = String.format("%s?productId=%s", reviewServiceURL, productId);
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

    @Override
    public Review createReview(Review body) {
        try {
            String url = reviewServiceURL;
            LOG.debug("Will post a new review to URL: {}", url);

            Review review = restTemplate.postForObject(url, body, Review.class);
            LOG.debug("Created a review with id: {}", review.getProductId());

            return review;

        } catch (HttpClientErrorException ex) {
            throw handleHttpClientException(ex);
        }
    }

    @Override
    public void deleteReviews(int productId) {
        try {
            String url = String.format("%s?productId=%s", reviewServiceURL, productId);
            LOG.debug("Will call the deleteReviews API on URL: {}", url);

            restTemplate.delete(url);

        } catch (HttpClientErrorException ex) {
            throw handleHttpClientException(ex);
        }
    }

    private RuntimeException handleHttpClientException(HttpClientErrorException ex) {
        switch (HttpStatus.resolve(ex.getStatusCode().value())) {
            case NOT_FOUND:
                return new NotFoundException(getErrorMessage(ex));
            case UNPROCESSABLE_ENTITY:
                return new InvalidInputException(getErrorMessage(ex));
            default:
                LOG.warn("Got an unexpected HTTP error: {}, will rethrow it", ex.getStatusCode());
                LOG.warn("Error body: {}", ex.getResponseBodyAsString());
                return ex;
        }
    }

    private String getErrorMessage(HttpClientErrorException ex) {
        try {
            return objectMapper.readValue(ex.getResponseBodyAsString(), HttpErrorInfo.class).getMessage();
        } catch (IOException e) {
            return e.getMessage();
        }
    }
}

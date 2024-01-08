package re.elio.microservices.core.review.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import re.elio.api.core.review.Review;
import re.elio.api.core.review.ReviewService;
import re.elio.api.event.Event;
import re.elio.api.exceptions.EventProcessingException;

import java.util.function.Consumer;

@Configuration
public class MessageProcessorConfig {
    private static final Logger LOG = LoggerFactory.getLogger(MessageProcessorConfig.class);

    private final ReviewService reviewService;

    public MessageProcessorConfig(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Bean
    public Consumer<Event<Integer, Review>> messageProcessor() {
        return event -> {
            LOG.info("Process message created at {}...", event.getEventCreatedAt());
            switch (event.getEventType()) {
                case CREATE:
                    Review review = event.getData();
                    LOG.info("Create review with ID: {}/{}", review.getProductId(), review.getReviewId());
                    reviewService.createReview(review).block();
                    break;
                case DELETE:
                    int productId = event.getKey();
                    LOG.info("Delete reviews with ProductID: {}", productId);
                    reviewService.deleteReviews(productId).block();
                    break;
                default:
                    String errorMessage = "Incorrect event type: " + event.getEventType() + ", expected a CREATE or DELETE event";
                    LOG.warn(errorMessage);
                    throw new EventProcessingException(errorMessage);
            }
            LOG.info("Message processing done!");
        };
    }
}

package re.elio.microservices.core.review.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.RestController;
import re.elio.api.core.review.Review;
import re.elio.api.core.review.ReviewService;
import re.elio.api.exceptions.InvalidInputException;
import re.elio.microservices.core.review.persistence.ReviewEntity;
import re.elio.microservices.core.review.persistence.ReviewRepository;
import re.elio.util.http.ServiceUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.List;
import java.util.logging.Level;

@RestController
public class ReviewServiceImpl implements ReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewServiceImpl.class);

    private final ServiceUtil serviceUtil;
    private final ReviewRepository repository;
    private final ReviewMapper mapper;

    private final Scheduler jdbcScheduler;

    @Autowired
    public ReviewServiceImpl(ServiceUtil serviceUtil, ReviewRepository repository, ReviewMapper mapper,
                             @Qualifier("jdbcScheduler") Scheduler jdbcScheduler) {
        this.serviceUtil = serviceUtil;
        this.repository = repository;
        this.mapper = mapper;
        this.jdbcScheduler = jdbcScheduler;
    }

    @Override
    public Flux<Review> getReviews(int productId) {
        if (productId < 1) throw new InvalidInputException("Invalid productId: " + productId);
        LOG.info("Will get reviews for product with ID: {}", productId);
        return Mono.fromCallable(() -> internalGetReviews(productId))
                .flatMapMany(Flux::fromIterable)
                .log(LOG.getName(), Level.FINE)
                .subscribeOn(jdbcScheduler);
    }

    private List<Review> internalGetReviews(int productId) {
        List<ReviewEntity> entityList = repository.findByProductId(productId);
        List<Review> list = mapper.entityListToApiList(entityList);
        list.forEach(e -> e.setServiceAddress(serviceUtil.getServiceAddress()));
        LOG.debug("/reviews response size: {}", list.size());
        return list;
    }

    @Override
    public Mono<Review> createReview(Review body) {
        if (body.getProductId() < 1) throw new InvalidInputException("Invalid productId: " + body.getProductId());
        return Mono.fromCallable(() -> internalCreateReview(body))
                .subscribeOn(jdbcScheduler);
    }

    private Review internalCreateReview(Review body) {
        try {
            ReviewEntity entity = mapper.apiToEntity(body);
            ReviewEntity newEntity = repository.save(entity);
            LOG.debug("createReview: created a review entity: {}/{}", body.getProductId(), body.getReviewId());
            return mapper.entityToApi(newEntity);

        } catch (DataIntegrityViolationException dive) {
            throw new InvalidInputException("Duplicate key, Product ID: " + body.getProductId() + ", Review ID: " + body.getReviewId());
        }
    }

    @Override
    public Mono<Void> deleteReviews(int productId) {
        if (productId < 1) throw new InvalidInputException("Invalid productId: " + productId);
        return Mono.fromRunnable(() -> internalDeleteReviews(productId))
                .subscribeOn(jdbcScheduler).then();
    }

    public void internalDeleteReviews(int productId) {
        LOG.debug("deleteReviews: tries to delete reviews for the product with productId: {}", productId);
        repository.deleteAll(repository.findByProductId(productId));
    }
}

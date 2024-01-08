package re.elio.microservices.core.recommendation.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RestController;
import re.elio.api.core.recommendation.Recommendation;
import re.elio.api.core.recommendation.RecommendationService;
import re.elio.api.exceptions.InvalidInputException;
import re.elio.microservices.core.recommendation.persistence.RecommendationEntity;
import re.elio.microservices.core.recommendation.persistence.RecommendationRepository;
import re.elio.util.http.ServiceUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.logging.Level;

@RestController
public class RecommendationServiceImpl implements RecommendationService {
    private static final Logger LOG = LoggerFactory.getLogger(RecommendationServiceImpl.class);
    private final ServiceUtil serviceUtil;
    private final RecommendationRepository repository;
    private final RecommendationMapper mapper;

    public RecommendationServiceImpl(ServiceUtil serviceUtil, RecommendationRepository repository, RecommendationMapper mapper) {
        this.serviceUtil = serviceUtil;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Flux<Recommendation> getRecommendations(int productId) {
        if (productId < 1) throw new InvalidInputException("Invalid productId: " + productId);
        LOG.info("Will get recommendations for product with ID: {}", productId);
        return repository.findByProductId(productId)
                .log(LOG.getName(), Level.FINE)
                .map(mapper::entityToApi)
                .map(e -> {
                    e.setServiceAddress(serviceUtil.getServiceAddress());
                    return e;
                });
    }

    @Override
    public Mono<Recommendation> createRecommendation(Recommendation body) {
        if (body.getProductId() < 1) throw new InvalidInputException("Invalid productId: " + body.getProductId());
        RecommendationEntity entity = mapper.apiToEntity(body);
        LOG.debug("createRecommendation: created a recommendation entity: {}/{}", body.getProductId(), body.getRecommendationId());
        return repository.save(entity)
                .log(LOG.getName(), Level.FINE)
                .onErrorMap(DuplicateKeyException.class, ex -> new InvalidInputException("Duplicate key, Product ID: " + body.getProductId() + ", Recommendation ID: " + body.getRecommendationId()))
                .map(mapper::entityToApi);
    }

    @Override
    public Mono<Void> deleteRecommendations(int productId) {
        if (productId < 1) throw new InvalidInputException("Invalid productId: " + productId);
        LOG.debug("deleteRecommendations: tries to delete recommendations for the product with productId: {}", productId);
        return repository.deleteAll(repository.findByProductId(productId));
    }
}

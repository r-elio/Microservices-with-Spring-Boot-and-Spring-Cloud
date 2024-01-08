package re.elio.microservices.core.product.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RestController;
import re.elio.api.core.product.Product;
import re.elio.api.core.product.ProductService;
import re.elio.api.exceptions.InvalidInputException;
import re.elio.api.exceptions.NotFoundException;
import re.elio.microservices.core.product.persistence.ProductEntity;
import re.elio.microservices.core.product.persistence.ProductRepository;
import re.elio.util.http.ServiceUtil;
import reactor.core.publisher.Mono;

import java.util.logging.Level;

@RestController
public class ProductServiceImpl implements ProductService {

    private static final Logger LOG = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ServiceUtil serviceUtil;
    private final ProductRepository repository;
    private final ProductMapper mapper;

    public ProductServiceImpl(ServiceUtil serviceUtil, ProductRepository repository, ProductMapper mapper) {
        this.serviceUtil = serviceUtil;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Mono<Product> getProduct(int productId) {
        LOG.debug("/product return the found product for productId = {}", productId);
        if (productId < 1) throw new InvalidInputException("Invalid productId: " + productId);
        LOG.info("Will get product info for id: {}", productId);
        return repository.findByProductId(productId)
                .switchIfEmpty(Mono.error(new NotFoundException("No product found for productId: " + productId)))
                .log(LOG.getName(), Level.FINE)
                .map(mapper::entityToApi)
                .map(e -> {
                    e.setServiceAddress(serviceUtil.getServiceAddress());
                    return e;
                });
    }

    @Override
    public Mono<Product> createProduct(Product body) {
        if (body.getProductId() < 1) throw new InvalidInputException("Invalid productId: " + body.getProductId());
        ProductEntity entity = mapper.apiToEntity(body);
        LOG.debug("createProduct: entity created for productId: {}", body.getProductId());
        return repository.save(entity)
                .log(LOG.getName(), Level.FINE)
                .onErrorMap(DuplicateKeyException.class, ex -> new InvalidInputException("Duplicate key, Product ID: " + body.getProductId()))
                .map(mapper::entityToApi);
    }

    @Override
    public Mono<Void> deleteProduct(int productId) {
        if (productId < 1) throw new InvalidInputException("Invalid productId: " + productId);
        LOG.debug("deleteProduct: tries to delete an entity with productId: {}", productId);
        return repository.findByProductId(productId)
                .log(LOG.getName(), Level.FINE)
                .map(repository::delete)
                .flatMap(e -> e);
    }
}

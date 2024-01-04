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
    public Product getProduct(int productId) {
        LOG.debug("/product return the found product for productId = {}", productId);
        if (productId < 1) throw new InvalidInputException("Invalid productId: " + productId);
        ProductEntity entity = repository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException("No product found for productId: " + productId));
        Product response = mapper.entityToApi(entity);
        response.setServiceAddress(serviceUtil.getServiceAddress());
        LOG.debug("getProduct: found productId: {}", response.getProductId());
        return response;
    }

    @Override
    public Product createProduct(Product body) {
        try {
            ProductEntity entity = mapper.apiToEntity(body);
            ProductEntity newEntity = repository.save(entity);
            LOG.debug("createProduct: entity created for productId: {}", body.getProductId());
            return mapper.entityToApi(newEntity);
        } catch (DuplicateKeyException dke) {
            throw new InvalidInputException("Duplicate key, Product Id: " + body.getProductId());
        }
    }

    @Override
    public void deleteProduct(int productId) {
        LOG.debug("deleteProduct: tries to delete an entity with productId: {}", productId);
        repository.findByProductId(productId).ifPresent(repository::delete);
    }

}

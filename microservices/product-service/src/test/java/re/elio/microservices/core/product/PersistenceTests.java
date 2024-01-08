package re.elio.microservices.core.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import re.elio.microservices.core.product.persistence.ProductEntity;
import re.elio.microservices.core.product.persistence.ProductRepository;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Objects;

@DataMongoTest
public class PersistenceTests extends MongoDbTestBase {
    @Autowired
    private ProductRepository repository;
    private ProductEntity savedEntity;

    @BeforeEach
    void setupDb() {
        StepVerifier.create(repository.deleteAll()).verifyComplete();
        ProductEntity entity = new ProductEntity(1, "n", 1);
        StepVerifier.create(repository.save(entity))
                .expectNextMatches(createdEntity -> {
                    savedEntity = createdEntity;
                    return areProductEqual(entity, savedEntity);
                })
                .verifyComplete();
    }

    @Test
    void create() {
        ProductEntity newEntity = new ProductEntity(2, "n", 2);
        StepVerifier.create(repository.save(newEntity))
                .expectNextMatches(createdEntity -> newEntity.getProductId() == createdEntity.getProductId())
                .verifyComplete();
        StepVerifier.create(repository.findById(newEntity.getId()))
                .expectNextMatches(foundEntity -> areProductEqual(newEntity, foundEntity))
                .verifyComplete();
        StepVerifier.create(repository.count()).expectNext(2L).verifyComplete();
    }

    @Test
    void update() {
        savedEntity.setName("n2");
        StepVerifier.create(repository.save(savedEntity))
                .expectNextMatches(updatedEntity -> updatedEntity.getName().equals("n2"))
                .verifyComplete();
        StepVerifier.create(repository.findById(savedEntity.getId()))
                .expectNextMatches(foundEntity -> foundEntity.getVersion() == 1 && foundEntity.getName().equals("n2"))
                .verifyComplete();
    }

    @Test
    void delete() {
        StepVerifier.create(repository.delete(savedEntity)).verifyComplete();
        StepVerifier.create(repository.existsById(savedEntity.getId()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void getByProductId() {
        StepVerifier.create(repository.findByProductId(savedEntity.getProductId()))
                .expectNextMatches(foundEntity -> areProductEqual(savedEntity, foundEntity))
                .verifyComplete();
    }

    @Test
    void duplicateError() {
        ProductEntity entity = new ProductEntity(savedEntity.getProductId(), "n", 1);
        StepVerifier.create(repository.save(entity)).expectError(DuplicateKeyException.class).verify();
    }

    @Test
    void optimisticLockError() {
        // Store the saved entity in two separate entity objects
        ProductEntity entity1 = repository.findById(savedEntity.getId()).block();
        ProductEntity entity2 = repository.findById(savedEntity.getId()).block();

        // Update the entity using the first entity object
        entity1.setName("n1");
        repository.save(entity1).block();

        // Update the entity using the second entity object.
        // This should fail since the second entity now holds an old version number,
        // that is, an Optimistic Lock Error
        StepVerifier.create(repository.save(entity2)).expectError(OptimisticLockingFailureException.class).verify();

        // Get the updated entity from the database and verify its new state
        StepVerifier.create(repository.findById(savedEntity.getId()))
                .expectNextMatches(foundEntity -> foundEntity.getVersion() == 1 && foundEntity.getName().equals("n1"))
                .verifyComplete();
    }

    private boolean areProductEqual(ProductEntity expectedEntity, ProductEntity actualEntity) {
        return Objects.equals(expectedEntity.getId(), actualEntity.getId()) &&
                Objects.equals(expectedEntity.getVersion(), actualEntity.getVersion()) &&
                expectedEntity.getProductId() == actualEntity.getProductId() &&
                Objects.equals(expectedEntity.getName(), actualEntity.getName()) &&
                expectedEntity.getWeight() == actualEntity.getWeight();
    }

    @Test
    void testStepVerifier() {
        StepVerifier.create(Flux.just(1, 2, 3, 4)
                        .filter(n -> n % 2 == 0)
                        .map(n -> n * 2)
                        .log())
                .expectNext(4, 8)
                .verifyComplete();
    }
}
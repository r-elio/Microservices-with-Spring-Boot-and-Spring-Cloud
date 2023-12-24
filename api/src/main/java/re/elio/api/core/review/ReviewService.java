package re.elio.api.core.review;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

public interface ReviewService {
    /**
     * Sample usage: "cur $HOST:$PORT/review?productId=1"
     * 
     * @param productId ID of the product
     * @return the reviews of the product
     */
    @GetMapping(value = "/review", produces = "application/json")
    List<Review> getReviews(@RequestParam(value = "productId", required = true) int productId);
}

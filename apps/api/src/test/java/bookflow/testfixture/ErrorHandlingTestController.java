package bookflow.testfixture;

import com.bookflow.shared.error.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/errors")
public class ErrorHandlingTestController {

    @PostMapping(path = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> validateBody(@Valid @RequestBody TestRequest request) {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/parameter")
    ResponseEntity<Void> validateParameter(
            @RequestParam(name = "limit") @Min(1) @Max(10) int limit
    ) {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/required")
    ResponseEntity<Void> requireParameter(@RequestParam(name = "name") String name) {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/resource-not-found")
    ResponseEntity<Void> resourceNotFound() {
        throw new ResourceNotFoundException("The requested test resource was not found.");
    }

    @GetMapping("/unexpected")
    ResponseEntity<Void> unexpected() {
        throw new IllegalStateException("fake-secret-internal-detail");
    }

    public record TestRequest(
            @NotBlank String name,
            @Size(min = 3, max = 5) String code
    ) {
    }
}

package dev.abbah.bookstore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookStoreApplicationTests {

  @Test
  void contextLoads() {
    // No body: the assertion is that the application context starts, which @SpringBootTest
    // already fails the test on.
  }

}

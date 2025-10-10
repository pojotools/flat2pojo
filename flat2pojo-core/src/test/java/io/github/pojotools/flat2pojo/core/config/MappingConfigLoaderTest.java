package io.github.pojotools.flat2pojo.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MappingConfigLoaderTest {

  @Test
  void validateRelativePaths_whenParentDeclaredBeforeChild_succeeds() {
    // This is the correct order - parent before child, using relative keyPaths
    String yaml =
        """
        separator: "/"
        lists:
          - path: "parent"
            keyPaths: ["id"]
          - path: "parent/child"
            keyPaths: ["id"]
        """;

    // Should not throw
    MappingConfig config = MappingConfigLoader.fromYaml(yaml);
    assertThat(config.lists()).hasSize(2);
  }

  @Test
  void validateKeyPaths_whenAbsolutePath_throwsValidationException() {
    // keyPaths must be relative to the list path
    String yaml =
        """
        separator: "/"
        lists:
          - path: "order/items"
            keyPaths: ["order/items/id"]
        """;

    MappingConfig config = MappingConfigLoader.fromYaml(yaml);
    assertThatThrownBy(() -> MappingConfigLoader.validateHierarchy(config))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("must be relative")
        .hasMessageContaining("order/items/id");
  }
}

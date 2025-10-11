package io.github.pojotools.flat2pojo.core.config;

import io.github.pojotools.flat2pojo.core.config.MappingConfig.ListRule;
import io.github.pojotools.flat2pojo.core.config.MappingConfig.OrderBy;
import io.github.pojotools.flat2pojo.core.util.PathOps;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MappingConfigLoader {
  private MappingConfigLoader() {}

  public static MappingConfig fromYaml(final String yaml) {
    return YamlConfigParser.parse(yaml);
  }

  public static void validateHierarchy(final MappingConfig config) {
    final HierarchyValidator validator = new HierarchyValidator(config);
    validator.validate();
  }

  private static class HierarchyValidator {
    private final String separator;
    private final Map<String, Integer> declarationOrder;
    private final Set<String> listPaths;
    private final List<ListRule> listRules;

    HierarchyValidator(MappingConfig cfg) {
      this.separator = cfg.separator();
      this.listRules = cfg.lists();
      this.declarationOrder = buildDeclarationOrderMap();
      this.listPaths = buildListPathsSet();
    }

    private Map<String, Integer> buildDeclarationOrderMap() {
      Map<String, Integer> order = new HashMap<>();
      for (int i = 0; i < listRules.size(); i++) {
        order.put(listRules.get(i).path(), i);
      }
      return order;
    }

    private Set<String> buildListPathsSet() {
      Set<String> paths = new HashSet<>();
      for (ListRule rule : listRules) {
        paths.add(rule.path());
      }
      return paths;
    }

    void validate() {
      for (ListRule rule : listRules) {
        validateSingleListRule(rule);
      }
    }

    private void validateSingleListRule(ListRule rule) {
      validateParentChildOrder(rule);
      validateKeyPathsAreRelative(rule);
      validateOrderByPathsAreRelative(rule);
      // validateImpliedParentLists is no longer needed with relative paths
    }

    private void validateKeyPathsAreRelative(ListRule rule) {
      String listPath = rule.path();
      String listPathPrefix = listPath + separator;

      for (String keyPath : rule.keyPaths()) {
        // keyPath must be relative (not start with listPath prefix)
        if (keyPath.startsWith(listPathPrefix)) {
          throw new ValidationException(
              "keyPath '"
                  + keyPath
                  + "' in list rule '"
                  + listPath
                  + "' must be relative, not absolute. Use '"
                  + keyPath.substring(listPathPrefix.length())
                  + "' instead.");
        }
      }
    }

    private void validateOrderByPathsAreRelative(ListRule rule) {
      String listPath = rule.path();
      String listPathPrefix = listPath + separator;

      for (OrderBy orderBy : rule.orderBy()) {
        String orderPath = orderBy.path();
        // orderBy path must be relative (not start with listPath prefix)
        if (orderPath.startsWith(listPathPrefix)) {
          throw new ValidationException(
              "orderBy path '"
                  + orderPath
                  + "' in list rule '"
                  + listPath
                  + "' must be relative, not absolute. Use '"
                  + orderPath.substring(listPathPrefix.length())
                  + "' instead.");
        }
      }
    }

    private void validateParentChildOrder(ListRule rule) {
      String path = rule.path();
      String nearestAncestor = findNearestListAncestor(path);

      if (nearestAncestor != null
          && declarationOrder.get(nearestAncestor) > declarationOrder.get(path)) {
        throw new ValidationException(
            "List '" + path + "' must be declared after its parent list '" + nearestAncestor + "'");
      }
    }

    private String findNearestListAncestor(String path) {
      return PathOps.findParentPath(path, listPaths, separator);
    }
  }
}

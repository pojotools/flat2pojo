package io.github.pojotools.flat2pojo.core.config;

import io.github.pojotools.flat2pojo.core.config.MappingConfig.ConflictPolicy;
import io.github.pojotools.flat2pojo.core.config.MappingConfig.Direction;
import io.github.pojotools.flat2pojo.core.config.MappingConfig.ListRule;
import io.github.pojotools.flat2pojo.core.config.MappingConfig.Nulls;
import io.github.pojotools.flat2pojo.core.config.MappingConfig.OrderBy;
import io.github.pojotools.flat2pojo.core.config.MappingConfig.PrimitiveSplitRule;
import io.github.pojotools.flat2pojo.core.util.PathOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

@SuppressWarnings("unchecked")
public final class MappingConfigLoader {
  private MappingConfigLoader() {}

  public static MappingConfig fromYaml(final String yaml) {
    final Map<String, Object> root = new Yaml().load(yaml);
    if (root == null) {
      return MappingConfig.builder().build();
    }

    final ImmutableMappingConfig.Builder builder = MappingConfig.builder();
    parseBasicConfiguration(root, builder);
    parsePrimitiveRules(root, builder);
    parseListRules(root, builder);
    parseNullPolicy(root, builder);
    return builder.build();
  }

  private static void parseBasicConfiguration(
      final Map<String, Object> root, final ImmutableMappingConfig.Builder builder) {
    builder.separator((String) root.getOrDefault("separator", "/"));
    builder.allowSparseRows(Boolean.TRUE.equals(root.get("allowSparseRows")));

    final List<String> rootKeys = (List<String>) root.get("rootKeys");
    if (rootKeys != null) {
      builder.rootKeys(rootKeys);
    }
  }

  private static void parsePrimitiveRules(
      final Map<String, Object> root, final ImmutableMappingConfig.Builder builder) {
    List<Map<String, Object>> primitives = (List<Map<String, Object>>) root.get("primitives");
    if (primitives == null) {
      return;
    }

    for (Map<String, Object> primitive : primitives) {
      parseSinglePrimitiveRule(primitive, builder);
    }
  }

  private static void parseSinglePrimitiveRule(
      final Map<String, Object> primitive, final ImmutableMappingConfig.Builder builder) {
    Map<String, Object> split = (Map<String, Object>) primitive.get("split");
    if (split == null) {
      return;
    }

    String path = (String) primitive.get("path");
    String delimiter = (String) split.getOrDefault("delimiter", ",");
    boolean trim = Boolean.TRUE.equals(split.get("trim"));

    builder.addPrimitives(new PrimitiveSplitRule(path, delimiter, trim));
  }

  private static void parseListRules(
      final Map<String, Object> root, final ImmutableMappingConfig.Builder builder) {
    List<Map<String, Object>> lists = (List<Map<String, Object>>) root.get("lists");
    if (lists == null) {
      return;
    }

    for (Map<String, Object> listRule : lists) {
      parseSingleListRule(listRule, builder);
    }
  }

  private static void parseSingleListRule(
      final Map<String, Object> listRule, final ImmutableMappingConfig.Builder builder) {
    String path = (String) listRule.get("path");
    List<String> keyPaths = (List<String>) listRule.getOrDefault("keyPaths", List.of());
    List<OrderBy> orderBy = parseOrderByRules(listRule);
    boolean dedupe = !Boolean.FALSE.equals(listRule.get("dedupe"));
    ConflictPolicy conflictPolicy = parseConflictPolicy(listRule);

    builder.addLists(new ListRule(path, keyPaths, orderBy, dedupe, conflictPolicy));
  }

  private static List<OrderBy> parseOrderByRules(Map<String, Object> listRule) {
    List<Map<String, Object>> orderByList = (List<Map<String, Object>>) listRule.get("orderBy");
    if (orderByList == null) {
      return new ArrayList<>();
    }

    List<OrderBy> orderBy = new ArrayList<>();
    for (Map<String, Object> orderSpec : orderByList) {
      orderBy.add(parseSingleOrderBy(orderSpec));
    }
    return orderBy;
  }

  private static OrderBy parseSingleOrderBy(Map<String, Object> orderSpec) {
    String path = (String) orderSpec.get("path");
    Direction direction = parseDirection((String) orderSpec.getOrDefault("direction", "asc"));
    Nulls nulls = parseNulls((String) orderSpec.getOrDefault("nulls", "last"));
    return new OrderBy(path, direction, nulls);
  }

  private static Direction parseDirection(String directionString) {
    return Direction.valueOf(directionString.toLowerCase(java.util.Locale.ROOT));
  }

  private static Nulls parseNulls(String nullsString) {
    return Nulls.valueOf(nullsString.toLowerCase(java.util.Locale.ROOT));
  }

  private static ConflictPolicy parseConflictPolicy(final Map<String, Object> listRule) {
    final String policyString = (String) listRule.getOrDefault("onConflict", "error");
    return ConflictPolicy.valueOf(policyString.trim());
  }

  private static void parseNullPolicy(
      final Map<String, Object> root, final ImmutableMappingConfig.Builder builder) {
    Object nullPolicyObject = root.get("nullPolicy");
    if (!(nullPolicyObject instanceof Map<?, ?> nullPolicyMap)) {
      return;
    }

    boolean blanksAsNulls = parseBlanksAsNulls(nullPolicyMap.get("blanksAsNulls"));
    builder.nullPolicy(new MappingConfig.NullPolicy(blanksAsNulls));
  }

  private static boolean parseBlanksAsNulls(Object blanksAsNullsValue) {
    if (blanksAsNullsValue instanceof Boolean booleanValue) {
      return booleanValue;
    }
    return blanksAsNullsValue != null
        && Boolean.parseBoolean(String.valueOf(blanksAsNullsValue));
  }

  public static void validateHierarchy(final MappingConfig cfg) {
    final HierarchyValidator validator = new HierarchyValidator(cfg);
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
      validateImpliedParentLists(rule);
    }

    private void validateParentChildOrder(ListRule rule) {
      String path = rule.path();
      String nearestAncestor = findNearestListAncestor(path);

      if (nearestAncestor != null
          && declarationOrder.get(nearestAncestor) > declarationOrder.get(path)) {
        throw new ValidationException(
            "List '"
                + path
                + "' must be declared after its parent list '"
                + nearestAncestor
                + "'");
      }
    }

    private void validateImpliedParentLists(ListRule rule) {
      String path = rule.path();
      for (String keyPath : rule.keyPaths()) {
        validateImpliedParent(path, keyPath);
      }
    }

    private void validateImpliedParent(String listPath, String keyPath) {
      String impliedParent = longestCommonPrefixPath(listPath, keyPath, separator);

      if (impliedParent.isEmpty() || impliedParent.equals(listPath)) {
        return;
      }

      if (!listPaths.contains(impliedParent)) {
        throw new ValidationException(
            "Invalid list rule: '"
                + listPath
                + "' missing ancestor '"
                + impliedParent
                + "' (implied by keyPath '"
                + keyPath
                + "')");
      }

      if (declarationOrder.get(impliedParent) > declarationOrder.get(listPath)) {
        throw new ValidationException(
            "List '"
                + listPath
                + "' must be declared after its parent list '"
                + impliedParent
                + "'");
      }
    }

    private String findNearestListAncestor(String path) {
      int separatorIndex = path.lastIndexOf(separator);
      while (separatorIndex > 0) {
        String prefix = path.substring(0, separatorIndex);
        if (listPaths.contains(prefix)) {
          return prefix;
        }
        separatorIndex = prefix.lastIndexOf(separator);
      }
      return null;
    }
  }

  private static String longestCommonPrefixPath(final String a, final String b, final String sep) {
    final List<String> as = PathOps.splitPath(a, sep);
    final List<String> bs = PathOps.splitPath(b, sep);
    final int n = Math.min(as.size(), bs.size());
    int i = 0;
    while (i < n && as.get(i).equals(bs.get(i))) {
      i++;
    }
    if (i == 0) {
      return "";
    }
    return String.join(sep, as.subList(0, i));
  }
}

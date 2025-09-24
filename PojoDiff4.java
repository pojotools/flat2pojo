package io.flat2pojo.diff;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/** Identity-aware, configurable deep diff for POJOs/JsonNodes using Jackson. */
public final class PojoDiff {
    private PojoDiff() {}

    // =================================================================================================
    // Public API
    // =================================================================================================

    public enum Kind { ADDED, REMOVED, CHANGED }

    public static final class Entry {
        public final String path;       // JSON Pointer
        public final Kind kind;
        public final JsonNode oldValue; // null for ADDED
        public final JsonNode newValue; // null for REMOVED
        public Entry(String path, Kind kind, JsonNode oldValue, JsonNode newValue) {
            this.path = path; this.kind = kind; this.oldValue = oldValue; this.newValue = newValue;
        }
        @Override public String toString() {
            return kind + " @ " + path + " : " +
                    (oldValue==null?"∅":oldValue) + " -> " + (newValue==null?"∅":newValue);
        }
    }

    /** Create a new configuration. */
    public static Config config() { return new Config(); }

    /** Compare two POJOs (same type). Auto-infers type hints from leftPojo unless disabled. */
    public static List<Entry> diff(Object leftPojo, Object rightPojo, ObjectMapper mapper, Config cfg) {
        if (cfg.autoInferTypes && cfg.typeHints == null && leftPojo != null) {
            cfg.typeHints(inferTypeHints(leftPojo.getClass(), mapper, cfg));
        }
        JsonNode left = mapper.valueToTree(leftPojo);
        JsonNode right = mapper.valueToTree(rightPojo);
        return diff(left, right, cfg);
    }

    /** Compare two JsonNodes with configuration. */
    public static List<Entry> diff(JsonNode left, JsonNode right, Config cfg) {
        JsonNode nl = normalize(left, cfg);
        JsonNode nr = normalize(right, cfg);
        List<Entry> out = new ArrayList<>();
        walk(nl, nr, JsonPointer.compile(""), cfg, out);
        return out;
    }

    /** Compare two lists of root POJOs by a single identity field (e.g., "id/identifier"). */
    public static <T> List<Entry> diffList(
            List<T> left, List<T> right, ObjectMapper mapper, Config cfg, String rootIdFieldOrPointer) {

        ObjectNode L = JsonNodeFactory.instance.objectNode();
        ObjectNode R = JsonNodeFactory.instance.objectNode();
        L.set("__root", mapper.valueToTree(left));
        R.set("__root", mapper.valueToTree(right));

        Config tmp = cfg.copy();
        tmp.list("/__root", rootIdFieldOrPointer);
        tmp.typeHintBaseDepth(1); // strip /<rootKey>/ when resolving types

        if (tmp.autoInferTypes && tmp.typeHints == null) {
            Class<?> clazz = firstNonNullClass(left, right);
            if (clazz != null) tmp.typeHints(inferTypeHints(clazz, mapper, tmp));
        }

        List<Entry> diffs = diff(L, R, tmp);
        // remove "/__root" from paths
        List<Entry> remapped = new ArrayList<>(diffs.size());
        for (Entry e : diffs) {
            String p = e.path.startsWith("/__root") ? e.path.substring("/__root".length()) : e.path;
            remapped.add(new Entry(p.isEmpty()?"/":p, e.kind, e.oldValue, e.newValue));
        }
        return remapped;
    }

    /** Compare two maps keyed by root keys (e.g., id → POJO). */
    public static <T> List<Entry> diffMap(
            Map<String, T> left, Map<String, T> right, ObjectMapper mapper, Config cfg) {

        ObjectNode L = JsonNodeFactory.instance.objectNode();
        ObjectNode R = JsonNodeFactory.instance.objectNode();
        if (left != null)  left.forEach((k,v) -> L.set(safeKey(k), mapper.valueToTree(v)));
        if (right != null) right.forEach((k,v) -> R.set(safeKey(k), mapper.valueToTree(v)));

        Config tmp = cfg.copy();
        tmp.typeHintBaseDepth(1); // strip /<rootKey>/

        return diff(L, R, tmp);
    }

    private static String safeKey(String k) { return (k == null || k.isEmpty()) ? "<null>" : k; }
    private static <T> Class<?> firstNonNullClass(List<T> a, List<T> b) {
        if (a != null) for (T t : a) if (t != null) return t.getClass();
        if (b != null) for (T t : b) if (t != null) return t.getClass();
        return null;
    }

    // =================================================================================================
    // Configuration
    // =================================================================================================

    public enum InterfaceAccessorStyle {
        /** JavaBean for interfaces: "getX" → "x", "isX" → "x" */
        BEAN_CONVENTIONS,
        /** Immutable-interface style: method name IS the property name (unless @JsonProperty overrides) */
        METHOD_NAME
    }

    public static final class Config {
        private final List<ListRule> listRules = new ArrayList<>();

        private final Set<String> ignoredExact = new HashSet<>();
        private final List<String> ignoredPrefixes = new ArrayList<>();
        private final List<Pattern> ignoredPatterns = new ArrayList<>();

        private final Map<String, BiPredicate<JsonNode, JsonNode>> equivalencesByExact = new HashMap<>();
        private final List<PathPredicateEquivalence> equivalencesByPattern = new ArrayList<>();

        // Type comparators + hints
        private final Map<Class<?>, BiPredicate<JsonNode, JsonNode>> typeComparators = new HashMap<>();
        private TypeHints typeHints;
        private boolean autoInferTypes = true;
        private int typeHintBaseDepth = 0; // strip N leading segments (e.g., /<rootKey>/...)

        // Leaf-type controls to stop scanning into java.time & other JDK internals
        private final Set<Class<?>> extraLeafTypes = new HashSet<>();
        private final Set<String> leafPackagePrefixes = new LinkedHashSet<>(List.of(
                "java.time.",
                "java.util.regex.",
                "java.net.",
                "java.sql."
        ));

        // Interface accessor handling
        private InterfaceAccessorStyle interfaceAccessorStyle = InterfaceAccessorStyle.METHOD_NAME;
        private boolean includeDefaultMethodsOnInterfaces = true; // include non-abstract no-arg accessors

        // ---- List rules
        /** Treat the array at {@code arrayPointer} as a map keyed by {@code idFieldOrPointer}. */
        public Config list(String arrayPointer, String idFieldOrPointer) {
            listRules.add(new ListRule(arrayPointer, idFieldOrPointer));
            return this;
        }

        // ---- Ignores
        public Config ignore(String exactPointer) { ignoredExact.add(exactPointer); return this; }
        public Config ignorePrefix(String pointerPrefix) { ignoredPrefixes.add(pointerPrefix); return this; }
        public Config ignorePattern(String regex) { ignoredPatterns.add(Pattern.compile(regex)); return this; }
        public Config ignoreGlob(String glob) { return ignorePattern(globToRegex(glob)); }

        boolean isIgnored(JsonPointer path) {
            String p = path.toString();
            if (ignoredExact.contains(p)) return true;
            for (String pref : ignoredPrefixes) {
                String prefAdj = pref.endsWith("/") ? pref : pref + "/";
                if (p.equals(pref) || p.startsWith(prefAdj)) return true;
            }
            for (Pattern pattern : ignoredPatterns) {
                if (pattern.matcher(p).matches()) return true;
            }
            return false;
        }

        // ---- Per-path equivalences
        public Config equivalent(String exactPointer, BiPredicate<JsonNode, JsonNode> eq) {
            equivalencesByExact.put(exactPointer, eq); return this;
        }
        public Config equivalentPattern(String regex, BiPredicate<JsonNode, JsonNode> eq) {
            equivalencesByPattern.add(new PathPredicateEquivalence(Pattern.compile(regex), eq)); return this;
        }
        public Config equivalentGlob(String glob, BiPredicate<JsonNode, JsonNode> eq) {
            return equivalentPattern(globToRegex(glob), eq);
        }
        Optional<BiPredicate<JsonNode, JsonNode>> equivalenceFor(JsonPointer path) {
            String p = path.toString();
            BiPredicate<JsonNode, JsonNode> eq = equivalencesByExact.get(p);
            if (eq != null) return Optional.of(eq);
            for (PathPredicateEquivalence e : equivalencesByPattern) {
                if (e.pattern.matcher(p).matches()) return Optional.of(e.eq);
            }
            return Optional.empty();
        }

        // ---- Type comparators
        public <T> Config typeEq(Class<T> type, BiPredicate<JsonNode, JsonNode> eq) {
            typeComparators.put(type, eq); return this;
        }
        BiPredicate<JsonNode, JsonNode> comparatorForClass(Class<?> raw) {
            Class<?> c = raw;
            while (c != null) {
                BiPredicate<JsonNode, JsonNode> eq = typeComparators.get(c);
                if (eq != null) return eq;
                c = c.getSuperclass();
            }
            for (Map.Entry<Class<?>, BiPredicate<JsonNode, JsonNode>> e : typeComparators.entrySet()) {
                if (e.getKey().isInterface() && e.getKey().isAssignableFrom(raw)) return e.getValue();
            }
            return null;
        }
        public Config typeHints(TypeHints hints) { this.typeHints = hints; return this; }
        public Config autoInferTypes(boolean enabled) { this.autoInferTypes = enabled; return this; }
        public Config typeHintBaseDepth(int depth) { this.typeHintBaseDepth = Math.max(0, depth); return this; }

        // ---- Leaf controls
        public Config leafType(Class<?> type) { extraLeafTypes.add(type); return this; }
        public Config leafPackage(String pkgPrefix) { leafPackagePrefixes.add(pkgPrefix); return this; }

        // ---- Interface accessor style
        public Config interfaceAccessorStyle(InterfaceAccessorStyle style) { this.interfaceAccessorStyle = style; return this; }
        public Config includeDefaultInterfaceMethods(boolean include) { this.includeDefaultMethodsOnInterfaces = include; return this; }

        // Internal access
        List<ListRule> listRules() { return listRules; }
        int baseDepth() { return typeHintBaseDepth; }
        TypeHints typeHints() { return typeHints; }
        boolean autoInferTypes() { return autoInferTypes; }
        Set<Class<?>> extraLeafTypes() { return extraLeafTypes; }
        Set<String> leafPackagePrefixes() { return leafPackagePrefixes; }
        InterfaceAccessorStyle ifaceStyle() { return interfaceAccessorStyle; }
        boolean includeDefaultInterfaceMethods() { return includeDefaultMethodsOnInterfaces; }

        Config copy() {
            Config c = new Config();
            c.listRules.addAll(this.listRules);
            c.ignoredExact.addAll(this.ignoredExact);
            c.ignoredPrefixes.addAll(this.ignoredPrefixes);
            c.ignoredPatterns.addAll(this.ignoredPatterns);
            c.equivalencesByExact.putAll(this.equivalencesByExact);
            c.equivalencesByPattern.addAll(this.equivalencesByPattern);
            c.typeComparators.putAll(this.typeComparators);
            c.typeHints = this.typeHints;
            c.autoInferTypes = this.autoInferTypes;
            c.typeHintBaseDepth = this.typeHintBaseDepth;
            c.extraLeafTypes.addAll(this.extraLeafTypes);
            c.leafPackagePrefixes.addAll(this.leafPackagePrefixes);
            c.interfaceAccessorStyle = this.interfaceAccessorStyle;
            c.includeDefaultMethodsOnInterfaces = this.includeDefaultMethodsOnInterfaces;
            return c;
        }
    }

    private record PathPredicateEquivalence(Pattern pattern, BiPredicate<JsonNode, JsonNode> eq) {}

    // =================================================================================================
    // Type hints (map JSON paths to Java types so type comparators can apply)
    // =================================================================================================

    public static final class TypeHints {
        private final Map<String, JavaType> pointerToType = new HashMap<>();
        public TypeHints put(String jsonPointer, JavaType type) { pointerToType.put(jsonPointer, type); return this; }
        JavaType resolveExact(JsonPointer p) { return pointerToType.get(p.toString()); }
        boolean contains(String ptr) { return pointerToType.containsKey(ptr); }
    }

    public static TypeHints inferTypeHints(Class<?> rootType, ObjectMapper mapper, Config userCfg) {
        TypeHints hints = new TypeHints();
        SerializationConfig sc = mapper.getSerializationConfig();
        JavaType root = mapper.constructType(rootType);
        // Use a recursion-path guard to avoid cycles, not a global set
        Deque<JavaType> stack = new ArrayDeque<>();
        buildHints("", root, hints, mapper, sc, stack, userCfg);
        return hints;
    }

    private static void buildHints(String basePtr, JavaType jt, TypeHints hints,
                                   ObjectMapper mapper, SerializationConfig sc,
                                   Deque<JavaType> recursionStack, Config userCfg) {
        if (jt == null) return;
        jt = unwrapOptionals(jt, mapper);
        if (jt == null) return;
        if (recursionStack.contains(jt)) return; // break cycles only
        recursionStack.push(jt);

        try {
            // Leaf short-circuit (Enums, primitives, String, java.time.*, etc.)
            if (isLeafType(jt, userCfg)) {
                if (!basePtr.isEmpty()) hints.put(basePtr, jt);
                return;
            }

            // Containers
            if (jt.isContainerType()) {
                if (jt.isCollectionLikeType()) {
                    JavaType elem = unwrapOptionals(jt.getContentType(), mapper);
                    String elemPtr = basePtr + "/*";
                    hints.put(elemPtr, elem);
                    buildHints(elemPtr, elem, hints, mapper, sc, recursionStack, userCfg);
                    return;
                } else if (jt.isMapLikeType()) {
                    JavaType val = unwrapOptionals(jt.getContentType(), mapper);
                    String valPtr = basePtr + "/*";
                    hints.put(valPtr, val);
                    buildHints(valPtr, val, hints, mapper, sc, recursionStack, userCfg);
                    return;
                }
            }

            if (jt.isJavaLangObject()) return;

            // Gather from Jackson bean properties
            BeanDescription bd = sc.introspect(jt);
            List<BeanPropertyDefinition> props = bd.findProperties();
            Set<String> seenNames = new HashSet<>();
            for (BeanPropertyDefinition prop : props) {
                String name = prop.getName();
                JavaType pt = unwrapOptionals(prop.getPrimaryType(), mapper);
                if (pt == null) continue;
                seenNames.add(name);
                String ptr = basePtr + "/" + escape(name);
                hints.put(ptr, pt);
                buildHints(ptr, pt, hints, mapper, sc, recursionStack, userCfg);
            }

            // Also scan interface accessor methods (merge; do not require "none found")
            if (jt.isInterface()) {
                Map<String, JavaType> more = collectInterfaceAccessors(jt.getRawClass(), mapper, userCfg);
                for (Map.Entry<String, JavaType> e : more.entrySet()) {
                    String name = e.getKey();
                    if (seenNames.contains(name)) continue;
                    JavaType pt = unwrapOptionals(e.getValue(), mapper);
                    if (pt == null) continue;
                    String ptr = basePtr + "/" + escape(name);
                    hints.put(ptr, pt);
                    buildHints(ptr, pt, hints, mapper, sc, recursionStack, userCfg);
                }
            }
        } finally {
            recursionStack.pop();
        }
    }

    /** Unwrap Optional<T> (Jackson ReferenceType) and JDK Optional primitives to their value type. */
    private static JavaType unwrapOptionals(JavaType type, ObjectMapper mapper) {
        if (type == null) return null;

        if (type.isReferenceType()) {
            JavaType ref = type.getReferencedType();
            return unwrapOptionals(ref, mapper);
        }

        Class<?> raw = type.getRawClass();
        if (raw == java.util.OptionalInt.class) {
            return mapper.constructType(Integer.class);
        } else if (raw == java.util.OptionalLong.class) {
            return mapper.constructType(Long.class);
        } else if (raw == java.util.OptionalDouble.class) {
            return mapper.constructType(Double.class);
        } else if (raw == java.util.Optional.class) {
            JavaType ct = type.containedTypeCount() > 0 ? type.containedType(0) : null;
            return ct != null ? unwrapOptionals(ct, mapper) : mapper.constructType(Object.class);
        }
        return type;
    }

    /** Decide if a JavaType should be treated as leaf (scalar) during type inference. */
    private static boolean isLeafType(JavaType jt, Config cfg) {
        if (jt == null) return true;
        if (jt.isEnumType()) return true;

        Class<?> raw = jt.getRawClass();
        if (raw.isPrimitive()
                || Number.class.isAssignableFrom(raw)
                || raw == String.class
                || raw == Boolean.class
                || raw == Character.class
                || raw == BigDecimal.class
                || raw == java.math.BigInteger.class
                || raw == java.util.UUID.class
                || raw == byte[].class || raw == char[].class) {
            return true;
        }

        // Package prefix leaves (java.time.*, etc.)
        String name = raw.getName();
        for (String pref : cfg.leafPackagePrefixes()) {
            if (name.startsWith(pref)) return true;
        }

        // Common explicit temporal/date classes
        if (raw == java.util.Date.class || raw == java.util.Calendar.class
                || raw == Instant.class || raw == java.time.LocalDate.class
                || raw == java.time.LocalTime.class || raw == java.time.LocalDateTime.class
                || raw == java.time.OffsetDateTime.class || raw == java.time.OffsetTime.class
                || raw == java.time.ZonedDateTime.class || raw == java.time.ZoneId.class
                || raw == java.time.ZoneOffset.class || raw == java.time.Duration.class
                || raw == java.time.Period.class) {
            return true;
        }

        // Containers are not leaves
        if (jt.isReferenceType()) return false;
        if (jt.isContainerType() || jt.isCollectionLikeType() || jt.isMapLikeType()) return false;

        // User-specified leaf types
        for (Class<?> leaf : cfg.extraLeafTypes()) if (leaf.isAssignableFrom(raw)) return true;

        return false;
    }

    /**
     * For immutable interfaces (and general interfaces): collect property names from no-arg accessors.
     * - Honors @JsonProperty for naming.
     * - Skips @JsonProperty(access = READ_ONLY/WRITE_ONLY)? We only need name; we don't filter by access here.
     * - Skips @JsonIgnore methods.
     * - Naming policy depends on Config.interfaceAccessorStyle:
     *     METHOD_NAME     → property name is the method name (no JavaBean stripping)
     *     BEAN_CONVENTIONS→ JavaBean ("getX"/"isX")
     */
    private static Map<String, JavaType> collectInterfaceAccessors(Class<?> iface, ObjectMapper mapper, Config cfg) {
        Map<String, JavaType> out = new LinkedHashMap<>();
        for (Method m : iface.getMethods()) {
            int mod = m.getModifiers();
            if (Modifier.isStatic(mod)) continue;
            if (m.getParameterCount() != 0) continue;
            if (m.getReturnType() == Void.TYPE) continue;
            if (m.getDeclaringClass() == Object.class) continue;

            // Optionally include default methods
            if (!cfg.includeDefaultInterfaceMethods() && !Modifier.isAbstract(mod)) continue;

            // @JsonIgnore?
            com.fasterxml.jackson.annotation.JsonIgnore ign = m.getAnnotation(com.fasterxml.jackson.annotation.JsonIgnore.class);
            if (ign != null && ign.value()) continue;

            // Name
            String name;
            JsonProperty jp = m.getAnnotation(JsonProperty.class);
            if (jp != null && !jp.value().isEmpty()) {
                name = jp.value();
            } else if (cfg.ifaceStyle() == InterfaceAccessorStyle.METHOD_NAME) {
                name = m.getName(); // DO NOT strip "get"/"is"
            } else {
                // BEAN_CONVENTIONS
                String mn = m.getName();
                if (mn.startsWith("get") && mn.length() > 3) {
                    name = Character.toLowerCase(mn.charAt(3)) + mn.substring(4);
                } else if (mn.startsWith("is") && mn.length() > 2
                        && (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class)) {
                    name = Character.toLowerCase(mn.charAt(2)) + mn.substring(3);
                } else {
                    name = mn;
                }
            }

            out.put(name, mapper.constructType(m.getGenericReturnType()));
        }
        return out;
    }

    // =================================================================================================
    // List normalization (identity-aware arrays)
    // =================================================================================================

    private static final class ListRule {
        final JsonPointer at;     // array location
        final String idSpec;      // field or nested path like "id/identifier"
        final boolean idIsPointer;
        ListRule(String pointer, String idFieldOrPointer) {
            this.at = JsonPointer.compile(pointer);
            this.idSpec = idFieldOrPointer;
            this.idIsPointer = idFieldOrPointer.indexOf('/') >= 0;
        }
    }

    private static JsonNode normalize(JsonNode root, Config cfg) {
        return normalizeRecurse(root, JsonPointer.compile(""), cfg);
    }

    private static JsonNode normalizeRecurse(JsonNode node, JsonPointer here, Config cfg) {
        if (node == null || node.isNull() || node.isMissingNode()) return MissingNode.getInstance();

        Optional<ListRule> rule = cfg.listRules().stream().filter(r -> here.equals(r.at)).findFirst();
        if (rule.isPresent() && node.isArray()) {
            ObjectNode keyed = keyArray((ArrayNode) node, rule.get());
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            for (Iterator<String> it = keyed.fieldNames(); it.hasNext(); ) {
                String k = it.next();
                out.set(k, normalizeRecurse(keyed.get(k), here, cfg));
            }
            return wrapKeyed(out);
        }

        if (node.isObject()) {
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonPointer childPtr = here.append(segment(e.getKey()));
                out.set(e.getKey(), normalizeRecurse(e.getValue(), childPtr, cfg));
            }
            return out;
        }

        if (node.isArray()) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            for (int i = 0; i < node.size(); i++) {
                JsonPointer childPtr = here.append(segment(i));
                out.add(normalizeRecurse(node.get(i), childPtr, cfg));
            }
            return out;
        }

        return node;
    }

    private static ObjectNode keyArray(ArrayNode arr, ListRule r) {
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        for (JsonNode el : arr) {
            if (!el.isObject()) continue;
            String key = extractId((ObjectNode) el, r);
            if (key == null) key = "<null>";
            out.set(key, el);
        }
        return out;
    }

    private static String extractId(ObjectNode obj, ListRule r) {
        if (r.idIsPointer) {
            JsonPointer p = toPointer(r.idSpec);
            JsonNode n = obj.at(p);
            return (n == null || n.isMissingNode() || n.isNull()) ? null : n.asText();
        } else {
            JsonNode n = obj.get(r.idSpec);
            return (n == null || n.isNull()) ? null : n.asText();
        }
    }

    private static ObjectNode wrapKeyed(ObjectNode keyed) {
        ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
        wrapper.set("__keyed__", keyed);
        return wrapper;
    }
    private static boolean isKeyed(JsonNode n) { return n != null && n.isObject() && n.has("__keyed__"); }
    private static JsonNode unwrapKeyed(JsonNode n) { return isKeyed(n) ? n.get("__keyed__") : n; }

    // =================================================================================================
    // Diff walk
    // =================================================================================================

    private static void walk(JsonNode l, JsonNode r, JsonPointer path, Config cfg, List<Entry> out) {
        // 0) Ignore rule check
        if (cfg.isIgnored(path)) return;

        // 1) Per-path equivalence FIRST (even if one side is Missing)
        Optional<BiPredicate<JsonNode, JsonNode>> eqOpt = cfg.equivalenceFor(path);
        if (eqOpt.isPresent()) {
            JsonNode ln = (l == null) ? MissingNode.getInstance() : l;
            JsonNode rn = (r == null) ? MissingNode.getInstance() : r;
            if (eqOpt.get().test(ln, rn)) return;
        }

        // 2) Added / Removed
        boolean lm = (l == null) || l.isMissingNode();
        boolean rm = (r == null) || r.isMissingNode();
        if (lm && !rm) { out.add(new Entry(path.toString(), Kind.ADDED, null, r)); return; }
        if (rm && !lm) { out.add(new Entry(path.toString(), Kind.REMOVED, l, null)); return; }
        if (lm && rm) return;

        // 3) Node type mismatch
        if (l.getNodeType() != r.getNodeType()) {
            if (typeEqual(l, r, path, cfg)) return;
            out.add(new Entry(path.toString(), Kind.CHANGED, l, r));
            return;
        }

        // 4) Keyed-wrapper handling
        if (isKeyed(l) || isKeyed(r)) {
            JsonNode lo = unwrapKeyed(l);
            JsonNode ro = unwrapKeyed(r);
            if ((lo != l) ^ (ro != r)) {
                if (typeEqual(lo, ro, path, cfg)) return;
                out.add(new Entry(path.toString(), Kind.CHANGED, l, r));
                return;
            }
            l = lo; r = ro;
        }

        // 5) Switch by node type
        switch (l.getNodeType()) {
            case OBJECT -> {
                Set<String> names = new TreeSet<>();
                l.fieldNames().forEachRemaining(names::add);
                r.fieldNames().forEachRemaining(names::add);
                for (String name : names) {
                    JsonPointer childPtr = path.append(segment(name));
                    JsonNode lv = l.get(name);
                    JsonNode rv = r.get(name);
                    lv = (lv == null) ? MissingNode.getInstance() : lv;
                    rv = (rv == null) ? MissingNode.getInstance() : rv;
                    walk(lv, rv, childPtr, cfg, out);
                }
            }
            case ARRAY -> {
                int max = Math.max(l.size(), r.size());
                for (int i = 0; i < max; i++) {
                    JsonPointer childPtr = path.append(segment(i));
                    JsonNode lv = i < l.size() ? l.get(i) : MissingNode.getInstance();
                    JsonNode rv = i < r.size() ? r.get(i) : MissingNode.getInstance();
                    walk(lv, rv, childPtr, cfg, out);
                }
            }
            default -> {
                if (!Objects.equals(l, r)) {
                    if (typeEqual(l, r, path, cfg)) return;
                    out.add(new Entry(path.toString(), Kind.CHANGED, l, r));
                }
            }
        }
    }

    // =================================================================================================
    // Type equality: wildcard-aware + base-depth aware
    // =================================================================================================

    private static boolean typeEqual(JsonNode l, JsonNode r, JsonPointer path, Config cfg) {
        if (cfg.typeHints() == null) return false;
        JavaType hinted = resolveTypeWithWildcards(path, cfg.typeHints(), cfg.baseDepth());
        if (hinted == null) return false;
        BiPredicate<JsonNode, JsonNode> cmp = cfg.comparatorForClass(hinted.getRawClass());
        if (cmp == null) return false;
        JsonNode ln = (l == null) ? MissingNode.getInstance() : l;
        JsonNode rn = (r == null) ? MissingNode.getInstance() : r;
        return cmp.test(ln, rn);
    }

    /**
     * Resolve a JavaType for a JSON Pointer using:
     *  - baseDepth: strip N leading segments (for root-keyed diffs),
     *  - wildcard fallback: replace trailing segments progressively with "/*" until a hint is found.
     */
    private static JavaType resolveTypeWithWildcards(JsonPointer fullPath, TypeHints hints, int baseDepth) {
        String[] segs = splitPointer(fullPath);
        if (segs.length == 0) return null;
        int start = Math.min(baseDepth, segs.length);
        String[] tail = Arrays.copyOfRange(segs, start, segs.length);
        if (tail.length == 0) return null;

        // Exact
        String exact = toPointerString(tail);
        JavaType t = hints.resolveExact(JsonPointer.compile(exact));
        if (t != null) return t;

        // Replace trailing segments successively with *
        for (int i = tail.length - 1; i >= 0; i--) {
            String[] copy = Arrays.copyOf(tail, tail.length);
            copy[i] = "*";
            String ptr = toPointerString(copy);
            if (hints.contains(ptr)) return hints.resolveExact(JsonPointer.compile(ptr));
        }

        // Fully wildcarded of same depth
        String[] allWild = new String[tail.length];
        Arrays.fill(allWild, "*");
        String any = toPointerString(allWild);
        return hints.resolveExact(JsonPointer.compile(any));
    }

    private static String[] splitPointer(JsonPointer p) {
        String s = p.toString();
        if (s.isEmpty() || s.equals("/")) return new String[0];
        s = s.substring(1);
        return s.split("/");
    }

    private static String toPointerString(String[] segs) {
        StringBuilder sb = new StringBuilder();
        for (String s : segs) {
            sb.append('/');
            sb.append(s);
        }
        return sb.length()==0 ? "/" : sb.toString();
    }

    // =================================================================================================
    // Built-in equivalences (handy presets)
    // =================================================================================================

    public static final class Equivalences {
        private Equivalences() {}

        /** Treat 0 (numeric or "0") as equivalent to missing/null/empty string. */
        public static BiPredicate<JsonNode, JsonNode> zeroEqualsMissing() {
            return (a, b) -> {
                boolean am = isMissingLike(a);
                boolean bm = isMissingLike(b);
                if (am && bm) return true;
                if (am) return isZeroLike(b);
                if (bm) return isZeroLike(a);
                return isZeroLike(a) && isZeroLike(b);
            };
        }

        /** Numbers equal within epsilon (works for numeric strings too). */
        public static BiPredicate<JsonNode, JsonNode> numericWithin(double epsilon) {
            return (a, b) -> {
                Optional<BigDecimal> da = toDecimal(a);
                Optional<BigDecimal> db = toDecimal(b);
                if (da.isEmpty() || db.isEmpty()) return false;
                BigDecimal diff = da.get().subtract(db.get()).abs();
                return diff.compareTo(BigDecimal.valueOf(epsilon)) <= 0;
            };
        }

        /** Case-insensitive textual equality. */
        public static BiPredicate<JsonNode, JsonNode> caseInsensitive() {
            return (a, b) -> a.isTextual() && b.isTextual()
                    ? a.asText().equalsIgnoreCase(b.asText())
                    : Objects.equals(a, b);
        }

        /** Equality on strings ignoring surrounding whitespace. */
        public static BiPredicate<JsonNode, JsonNode> trimmedText() {
            return (a, b) -> a.isTextual() && b.isTextual()
                    ? a.asText().trim().equals(b.asText().trim())
                    : Objects.equals(a, b);
        }

        /** ZDT/ODT/Instant equal when truncated to millis (string ISO forms). */
        public static BiPredicate<JsonNode, JsonNode> zonedDateTimeTruncatedToMillisEqual() {
            return (a, b) -> {
                Optional<Instant> ia = parseToInstant(a);
                Optional<Instant> ib = parseToInstant(b);
                if (ia.isPresent() && ib.isPresent()) {
                    return ia.get().truncatedTo(ChronoUnit.MILLIS)
                             .equals(ib.get().truncatedTo(ChronoUnit.MILLIS));
                }
                // Fallback textual millisecond trim
                if (a.isTextual() && b.isTextual()) {
                    return truncateIsoMillis(a.asText()).equals(truncateIsoMillis(b.asText()));
                }
                return false;
            };
        }

        // ---- helpers
        private static boolean isMissingLike(JsonNode n) {
            return n == null || n.isMissingNode() || n.isNull()
                    || (n.isTextual() && n.asText().trim().isEmpty());
        }
        private static boolean isZeroLike(JsonNode n) {
            if (n == null || n.isNull() || n.isMissingNode()) return false;
            if (n.isNumber()) return n.decimalValue().compareTo(BigDecimal.ZERO) == 0;
            if (n.isTextual()) {
                String s = n.asText().trim();
                if (s.isEmpty()) return false;
                try { return new BigDecimal(s).compareTo(BigDecimal.ZERO) == 0; }
                catch (Exception ignored) { return false; }
            }
            return false;
        }
        private static Optional<BigDecimal> toDecimal(JsonNode n) {
            try {
                if (n.isNumber()) return Optional.of(n.decimalValue());
                if (n.isTextual()) return Optional.of(new BigDecimal(n.asText().trim()));
                return Optional.empty();
            } catch (Exception e) { return Optional.empty(); }
        }
        private static Optional<Instant> parseToInstant(JsonNode n) {
            try {
                if (n == null || n.isNull() || n.isMissingNode() || !n.isTextual()) return Optional.empty();
                String s = n.asText();
                try { return Optional.of(ZonedDateTime.parse(s).toInstant()); } catch (Exception ignored) {}
                try { return Optional.of(OffsetDateTime.parse(s).toInstant()); } catch (Exception ignored) {}
                try { return Optional.of(Instant.parse(s)); } catch (Exception ignored) {}
                return Optional.empty();
            } catch (Exception e) { return Optional.empty(); }
        }
        private static String truncateIsoMillis(String s) {
            int t = s.indexOf('T'); if (t < 0) return s;
            int dot = s.indexOf('.', t); if (dot < 0) return s;
            int end = dot + 1;
            while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
            String frac = s.substring(dot + 1, end);
            if (frac.length() <= 3) return s;
            return s.substring(0, dot + 1) + frac.substring(0, 3) + s.substring(end);
        }
    }

    // =================================================================================================
    // Simple Spring-style string comparator (punctuation -> '?')
    // =================================================================================================

    public static final class StringEquivalences {
        private StringEquivalences() {}

        /** Equal if, after normalization + mapping any punctuation to '?', both strings match. */
        public static BiPredicate<JsonNode, JsonNode> punctuationQuestionEquals() {
            return (a, b) -> {
                if (a == null || b == null || !a.isTextual() || !b.isTextual()) return false;
                String s1 = simplify(a.asText());
                String s2 = simplify(b.asText());
                boolean anyQ = s1.indexOf('?') >= 0 || s2.indexOf('?') >= 0;
                return anyQ && s1.equals(s2);
            };
        }

        private static String simplify(String s) {
            s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
            s = s.trim().replaceAll("\\s+", " ");
            StringBuilder out = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); ) {
                int cp = s.codePointAt(i);
                out.append(isPunct(cp) ? '?' : Character.toChars(cp));
                i += Character.charCount(cp);
            }
            return out.toString();
        }
        private static boolean isPunct(int cp) {
            int t = Character.getType(cp);
            return t == Character.CONNECTOR_PUNCTUATION
                || t == Character.DASH_PUNCTUATION
                || t == Character.START_PUNCTUATION
                || t == Character.END_PUNCTUATION
                || t == Character.OTHER_PUNCTUATION
                || t == Character.INITIAL_QUOTE_PUNCTUATION
                || t == Character.FINAL_QUOTE_PUNCTUATION;
        }
    }

    // =================================================================================================
    // Helpers
    // =================================================================================================

    private static JsonPointer segment(String field) {
        return JsonPointer.compile("/" + escape(field));
    }
    private static JsonPointer segment(int index) {
        return JsonPointer.compile("/" + index);
    }
    private static String escape(String raw) {
        return raw.replace("~", "~0").replace("/", "~1");
    }
    private static JsonPointer toPointer(String pathLike) {
        if (pathLike.startsWith("/")) return JsonPointer.compile(pathLike);
        String[] parts = pathLike.split("/");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append('/').append(escape(p));
        return JsonPointer.compile(sb.toString());
    }
    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        sb.append('^');
        for (int i = 0; i < glob.length();) {
            char c = glob.charAt(i);
            if (c == '*') {
                boolean dbl = (i + 1 < glob.length() && glob.charAt(i + 1) == '*');
                if (dbl) { sb.append(".*"); i += 2; }
                else { sb.append("[^/]*"); i++; }
            } else {
                if ("\\.[]{}()+-^$|".indexOf(c) >= 0) sb.append('\\');
                sb.append(c);
                i++;
            }
        }
        sb.append('$');
        return sb.toString();
    }
}
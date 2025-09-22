package io.flat2pojo.core.config;

import java.util.*;

public final class MappingConfig {
    private final String separator;
    private final boolean allowSparseRows;
    private final List<String> rootKeys;
    private final List<ListRule> lists;
    private final List<PrimitiveSplitRule> primitives;
    private final NullPolicy nullPolicy;

    private MappingConfig(Builder b) {
        this.separator = b.separator;
        this.allowSparseRows = b.allowSparseRows;
        this.rootKeys = List.copyOf(b.rootKeys);
        this.lists = List.copyOf(b.lists);
        this.primitives = List.copyOf(b.primitives);
        this.nullPolicy = b.nullPolicy;
    }

    // ======= GETTERS (used throughout the codebase) =======
    public String separator() { return separator; }
    public boolean allowSparseRows() { return allowSparseRows; }
    public List<String> rootKeys() { return rootKeys; }
    public List<ListRule> lists() { return lists; }
    public List<PrimitiveSplitRule> primitives() { return primitives; }
    public NullPolicy nullPolicy() { return nullPolicy; }

    // ======= Builder =======
    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private String separator = "/";
        private boolean allowSparseRows = false;
        private List<String> rootKeys = new ArrayList<>();
        private List<ListRule> lists = new ArrayList<>();
        private List<PrimitiveSplitRule> primitives = new ArrayList<>();
        private NullPolicy nullPolicy = new NullPolicy(false);

        public Builder separator(String s){ this.separator = s; return this; }
        public Builder allowSparseRows(boolean b){ this.allowSparseRows = b; return this; }
        public Builder addRootKey(String k){ this.rootKeys.add(k); return this; }
        public Builder rootKeys(List<String> ks){ this.rootKeys = new ArrayList<>(ks); return this; }
        public Builder addListRule(ListRule r){ this.lists.add(r); return this; }
        public Builder listRules(List<ListRule> rs){ this.lists = new ArrayList<>(rs); return this; }
        public Builder addPrimitiveRule(PrimitiveSplitRule r){ this.primitives.add(r); return this; }
        public Builder primitiveRules(List<PrimitiveSplitRule> rs){ this.primitives = new ArrayList<>(rs); return this; }
        public Builder nullPolicy(NullPolicy np){ this.nullPolicy = np; return this; }
        public MappingConfig build(){ return new MappingConfig(this); }
    }

    // ======= Value types (records) with getters by default =======
    public record NullPolicy(boolean blanksAsNulls) {}

    public record ListRule(
        String path,
        List<String> keyPaths,
        List<OrderBy> orderBy,
        boolean dedupe,
        ConflictPolicy onConflict
    ) {}

    public enum ConflictPolicy { error, lastWriteWins, firstWriteWins, merge }

    public record OrderBy(
        String path,
        Direction direction,
        Nulls nulls
    ) {}

    public enum Direction { asc, desc }
    public enum Nulls { first, last }

    public record PrimitiveSplitRule(
        String path,
        String delimiter,
        boolean trim
    ) {}
}

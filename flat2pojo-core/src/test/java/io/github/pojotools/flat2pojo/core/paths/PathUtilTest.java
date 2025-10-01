package io.github.pojotools.flat2pojo.core.paths;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PathUtilTest {

  @Test
  void split_withNullPath_returnsEmptyList() {
    List<String> result = PathUtil.split(null, "/");
    assertThat(result).isEmpty();
  }

  @Test
  void split_withEmptyPath_returnsEmptyList() {
    List<String> result = PathUtil.split("", "/");
    assertThat(result).isEmpty();
  }

  @Test
  void split_withSingleCharSeparator_splitsProperly() {
    List<String> result = PathUtil.split("a/b/c", "/");
    assertThat(result).containsExactly("a", "b", "c");
  }

  @Test
  void split_withSingleCharSeparator_handlesConsecutiveSeparators() {
    List<String> result = PathUtil.split("a//b///c", "/");
    assertThat(result).containsExactly("a", "", "b", "", "", "c");
  }

  @Test
  void split_withSingleCharSeparator_handlesLeadingSeparator() {
    List<String> result = PathUtil.split("/a/b", "/");
    assertThat(result).containsExactly("", "a", "b");
  }

  @Test
  void split_withSingleCharSeparator_handlesTrailingSeparator() {
    List<String> result = PathUtil.split("a/b/", "/");
    assertThat(result).containsExactly("a", "b", "");
  }

  @Test
  void split_withSingleCharSeparator_handlesPathWithoutSeparator() {
    List<String> result = PathUtil.split("simple", "/");
    assertThat(result).containsExactly("simple");
  }

  @Test
  void split_withMultiCharSeparator_splitsProperly() {
    List<String> result = PathUtil.split("a::b::c", "::");
    assertThat(result).containsExactly("a", "b", "c");
  }

  @Test
  void split_withMultiCharSeparator_handlesConsecutiveSeparators() {
    List<String> result = PathUtil.split("a::::b", "::");
    assertThat(result).containsExactly("a", "", "b");
  }

  @Test
  void split_withMultiCharSeparator_handlesLeadingSeparator() {
    List<String> result = PathUtil.split("::a::b", "::");
    assertThat(result).containsExactly("", "a", "b");
  }

  @Test
  void split_withMultiCharSeparator_handlesTrailingSeparator() {
    List<String> result = PathUtil.split("a::b::", "::");
    assertThat(result).containsExactly("a", "b", "");
  }

  @Test
  void split_withMultiCharSeparator_handlesPathWithoutSeparator() {
    List<String> result = PathUtil.split("simple", "::");
    assertThat(result).containsExactly("simple");
  }

  @Test
  void split_withDotSeparator_handlesComplexPath() {
    List<String> result = PathUtil.split("root.deeply.nested.field", ".");
    assertThat(result).containsExactly("root", "deeply", "nested", "field");
  }

  @Test
  void split_withLongMultiCharSeparator_splitsProperly() {
    List<String> result = PathUtil.split("a<-SEP->b<-SEP->c", "<-SEP->");
    assertThat(result).containsExactly("a", "b", "c");
  }

  @Test
  void join_withEmptyList_returnsEmptyString() {
    String result = PathUtil.join(List.of(), "/");
    assertThat(result).isEmpty();
  }

  @Test
  void join_withSingleElement_returnsElement() {
    String result = PathUtil.join(List.of("single"), "/");
    assertThat(result).isEqualTo("single");
  }

  @Test
  void join_withMultipleElements_joinsProperly() {
    String result = PathUtil.join(List.of("a", "b", "c"), "/");
    assertThat(result).isEqualTo("a/b/c");
  }

  @Test
  void join_withMultiCharSeparator_joinsProperly() {
    String result = PathUtil.join(List.of("a", "b", "c"), "::");
    assertThat(result).isEqualTo("a::b::c");
  }

  @Test
  void join_withEmptyElementsInList_preservesEmptyElements() {
    String result = PathUtil.join(List.of("a", "", "b"), "/");
    assertThat(result).isEqualTo("a//b");
  }

  @Test
  void isPrefixOf_whenPathsAreEqual_returnsTrue() {
    boolean result = PathUtil.isPrefixOf("a/b/c", "a/b/c", "/");
    assertThat(result).isTrue();
  }

  @Test
  void isPrefixOf_whenPrefixIsActualPrefix_returnsTrue() {
    boolean result = PathUtil.isPrefixOf("a/b", "a/b/c", "/");
    assertThat(result).isTrue();
  }

  @Test
  void isPrefixOf_whenPrefixIsNotPrefix_returnsFalse() {
    boolean result = PathUtil.isPrefixOf("a/c", "a/b/c", "/");
    assertThat(result).isFalse();
  }

  @Test
  void isPrefixOf_whenPrefixIsLongerThanPath_returnsFalse() {
    boolean result = PathUtil.isPrefixOf("a/b/c/d", "a/b/c", "/");
    assertThat(result).isFalse();
  }

  @Test
  void isPrefixOf_whenPartialMatchButNotPrefix_returnsFalse() {
    // "a/ba" is NOT a prefix of "a/b/c" even though "a/b" starts with "a/b"
    boolean result = PathUtil.isPrefixOf("a/ba", "a/b/c", "/");
    assertThat(result).isFalse();
  }

  @Test
  void isPrefixOf_withMultiCharSeparator_whenPrefixIsActualPrefix_returnsTrue() {
    boolean result = PathUtil.isPrefixOf("a::b", "a::b::c", "::");
    assertThat(result).isTrue();
  }

  @Test
  void isPrefixOf_withMultiCharSeparator_whenPrefixIsNotPrefix_returnsFalse() {
    boolean result = PathUtil.isPrefixOf("a::c", "a::b::c", "::");
    assertThat(result).isFalse();
  }

  @Test
  void roundTrip_splitAndJoin_producesOriginalPath() {
    String original = "a/b/c/d/e";
    String separator = "/";
    List<String> parts = PathUtil.split(original, separator);
    String rejoined = PathUtil.join(parts, separator);
    assertThat(rejoined).isEqualTo(original);
  }

  @Test
  void roundTrip_splitAndJoinWithMultiCharSeparator_producesOriginalPath() {
    String original = "root::deeply::nested::field";
    String separator = "::";
    List<String> parts = PathUtil.split(original, separator);
    String rejoined = PathUtil.join(parts, separator);
    assertThat(rejoined).isEqualTo(original);
  }
}

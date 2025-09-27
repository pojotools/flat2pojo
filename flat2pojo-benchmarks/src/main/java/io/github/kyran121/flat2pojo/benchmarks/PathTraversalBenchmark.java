package io.github.kyran121.flat2pojo.benchmarks;

import io.github.kyran121.flat2pojo.core.util.PathOps;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class PathTraversalBenchmark {

  private String shallowPath;
  private String deepPath;
  private String veryDeepPath;
  private String separator;

  @Setup
  public void setup() {
    separator = "/";
    shallowPath = "user/name";
    deepPath = "user/profile/contact/address/street";
    veryDeepPath = "organization/departments/engineering/teams/backend/members/senior/developers/profiles/personal/contact/emergency/primary/phone";
  }

  @Benchmark
  public void splitShallowPath(Blackhole bh) {
    List<String> result = PathOps.splitPath(shallowPath, separator);
    bh.consume(result);
  }

  @Benchmark
  public void splitDeepPath(Blackhole bh) {
    List<String> result = PathOps.splitPath(deepPath, separator);
    bh.consume(result);
  }

  @Benchmark
  public void splitVeryDeepPath(Blackhole bh) {
    List<String> result = PathOps.splitPath(veryDeepPath, separator);
    bh.consume(result);
  }

  @Benchmark
  public void nextSepShallowPath(Blackhole bh) {
    int result = PathOps.nextSep(shallowPath, 0, '/');
    bh.consume(result);
  }

  @Benchmark
  public void nextSepDeepPath(Blackhole bh) {
    int result = PathOps.nextSep(deepPath, 0, '/');
    bh.consume(result);
  }

  @Benchmark
  public void isUnderCheckShallow(Blackhole bh) {
    boolean result = PathOps.isUnder("user/profile/name", "user", separator);
    bh.consume(result);
  }

  @Benchmark
  public void isUnderCheckDeep(Blackhole bh) {
    boolean result = PathOps.isUnder(veryDeepPath, "organization/departments/engineering", separator);
    bh.consume(result);
  }

  @Benchmark
  public void tailAfterShallow(Blackhole bh) {
    String result = PathOps.tailAfter("user/profile/name", "user", separator);
    bh.consume(result);
  }

  @Benchmark
  public void tailAfterDeep(Blackhole bh) {
    String result = PathOps.tailAfter(veryDeepPath, "organization/departments/engineering/teams", separator);
    bh.consume(result);
  }

  @Benchmark
  public void baselineStringSplit(Blackhole bh) {
    String[] result = deepPath.split("/");
    bh.consume(Arrays.asList(result));
  }

  @Benchmark
  public void baselineStringStartsWith(Blackhole bh) {
    boolean result = veryDeepPath.startsWith("organization/departments/engineering");
    bh.consume(result);
  }

  @Benchmark
  public void pathSplitOptimized(Blackhole bh) {
    List<String> result = PathOps.splitPath(deepPath, separator);
    bh.consume(result);
  }
}
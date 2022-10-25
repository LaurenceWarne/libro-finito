package fin

import java.util.concurrent.TimeUnit

import scala.collection.mutable.ListBuffer

import org.openjdk.jmh.annotations._

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.All))
@State(Scope.Thread)
class FinitoBenchmark {

  @Benchmark
  @Fork(value = 2)
  @Measurement(iterations = 10, time = 1)
  @Warmup(iterations = 5, time = 1)
  def listImmutable() = {
    var ls = List[Int]()
    for (i <- (1 to 1000)) ls = i :: ls
    ls
  }

  @Benchmark
  @Fork(value = 2)
  @Measurement(iterations = 10, time = 1)
  @Warmup(iterations = 5, time = 1)
  def listMutable() = {
    val ls = ListBuffer[Int]()
    for (i <- (1 to 1000)) ls.addOne(i)
    ls
  }

}

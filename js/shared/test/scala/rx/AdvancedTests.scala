package rx
//
import acyclic.file

import scala.util.{Success, Failure}

import utest._
object AdvancedTests extends TestSuite{
  def tests = TestSuite{
//    'perf{
//      'init{
//        val start = System.currentTimeMillis()
//        var n = 0
//        while(System.currentTimeMillis() < start + 10000){
//          val (a, b, c, d, e, f) = Util.initGraph
//          n += 1
//        }
//        n
//      }
//      'propagations{
//        val (a, b, c, d, e, f) = Util.initGraph
//        val start = System.currentTimeMillis()
//        var n = 0
//
//        while(System.currentTimeMillis() < start + 10000){
//          a() = n
//          n += 1
//        }
//        n
//      }
//    }
    "nesting" - {
      "nestedRxs" - {
        val a = Var(1)
        val b = Rx{
          Rx{ a() } -> Rx{ math.random }
        }
        val r = b.now._2.now
        a() = 2
        assert(b.now._2.now == r)
      }
      "recalc" - {
        var source = 0
        val a = Rx{
          source
        }
        var i = 0
        val o = a.trigger{
          i += 1
        }
        assert(i == 1)
        assert(a.now == 0)
        source = 1
        assert(a.now == 0)
        a.recalc()
        assert(a.now == 1)
        assert(i == 2)
      }
      "multiset" - {
        val a = Var(1)
        val b = Var(1)
        val c = Var(1)
        val d = Rx{
          a() + b() + c()
        }
        var i = 0
        val o = d.trigger{
          i += 1
        }
        assert(i == 1)
        a() = 2
        assert(i == 2)
        b() = 2
        assert(i == 3)
        c() = 2
        assert(i == 4)

        Var.set(
          a -> 3,
          b -> 3,
          c -> 3
        )

        assert(i == 5)

        Var.set(
          Seq(
            a -> 4,
            b -> 5,
            c -> 6
          ):_*
        )

        assert(i == 6)
      }
      "webPage" - {
        var fakeTime = 123
        trait WebPage{
          def fTime = fakeTime
          val time = Var(fTime)
          def update(): Unit  = time() = fTime
          val html: Rx[String]
        }
        class HomePage extends WebPage {
          val html = Rx{"Home Page! time: " + time()}
        }
        class AboutPage extends WebPage {
          val html = Rx{"About Me, time: " + time()}
        }

        val url = Var("www.mysite.com/home")
        val page = Rx{
          url() match{
            case "www.mysite.com/home" => new HomePage()
            case "www.mysite.com/about" => new AboutPage()
          }
        }

        assert(page.now.html.now == "Home Page! time: 123")

        fakeTime = 234
        page.now.update()
        assert(page.now.html.now == "Home Page! time: 234")

        fakeTime = 345
        url() = "www.mysite.com/about"
        assert(page.now.html.now == "About Me, time: 345")

        fakeTime = 456
        page.now.update()
        assert(page.now.html.now == "About Me, time: 456")
      }

    }

    "combinators" - {
      "foreach" - {
        val a = Var(1)
        var count = 0
        val o = a.foreach{ x =>
          count = x + 1
        }
        assert(count == 2)
        a() = 4
        assert(count == 5)
      }
      "map" - {
        val a = Var(10)
        val b = Rx{ a() + 2 }
        val c = a.map(_*2)
        val d = b.map(_+3)
        assert(c.now == 20)
        assert(d.now == 15)
        a() = 1
        assert(c.now == 2)
        assert(d.now == 6)
      }

      "mapAll" - {
        val a = Var(10L)
        val b = Rx{ 100 / a() }
        val c = b.all.map{
          case Success(x) => Success(x * 2)
          case Failure(_) => Success(1337)
        }
        val d = b.all.map{
          case Success(x) => Failure(new Exception("No Error?"))
          case Failure(x) => Success(x.toString)
        }
        assert(c.now == 20)
        assert(d.toTry.isFailure)
        a() = 0
        assert(c.now == 1337)
        assert(d.toTry == Success("java.lang.ArithmeticException: / by zero"))
      }

      "filter" - {
        val a = Var(10)
        val b = a.filter(_ > 5)
        a() = 1
        assert(b.now == 10)
        a() = 6
        assert(b.now == 6)
        a() = 2
        assert(b.now == 6)
        a() = 19
        assert(b.now == 19)
      }
      "filterFirstFail" - {
        val a = Var(10)
        val b = a.filter(_ > 15)
        a() = 1
        assert(b.now == 10)

      }
      "filterAll" - {
        val a = Var(10L)
        val b = Rx{ 100 / a() }
        val c = b.all.filter(_.isSuccess)

        assert(c.now == 10)
        a() = 9
        assert(c.now == 11)
        a() = 0
        assert(c.now == 11)
        a() = 1
        assert(c.now == 100)
      }

      "reduce" - {
        val a = Var(2)
        val b = a.reduce(_ * _)
        // no-change means no-change
        a() = 2
        assert(b.now == 2)
        // only does something when you change
        a() = 3
        assert(b.now == 6)
        a() = 4
        assert(b.now == 24)
      }

      "reduceAll" - {
        val a = Var(1L)
        val b = Rx{ 100 / a() }
        val c = b.all.reduce{
          case (Success(a), Success(b)) => Success(a + b)
          case (Failure(a), Failure(b)) => Success(1337)
          case (Failure(a), Success(b)) => Failure(a)
          case (Success(a), Failure(b)) => Failure(b)
        }

        assert(c.now == 100)
        a() = 0
        assert(c.toTry.isFailure)
        a() = 10
        assert(c.toTry.isFailure)
        a() = 100
        assert(c.toTry.isFailure)
        a() = 0
        assert(c.now == 1337)
        a() = 10
        assert(c.now == 1347)
      }

      "killRx" - {
        val (a, b, c, d, e, f) = Util.initGraph

        assert(c.now == 3)
        assert(e.now == 7)
        assert(f.now == 26)
        a() = 3
        assert(c.now == 5)
        assert(e.now == 9)
        assert(f.now == 38)

        // Killing d stops it from updating, but the changes can still
        // propagate through e to reach f
        d.kill()
        a() = 1
        assert(f.now == 36)

        // After killing f, it stops updating but others continue to do so
        f.kill()
        a() = 3
        assert(c.now == 5)
        assert(e.now == 9)
        assert(f.now == 36)

        // After killing c, the everyone doesn't get updates anymore
        c.kill()
        a() = 1
        assert(c.now == 5)
        assert(e.now == 9)
        assert(f.now == 36)
      }
    }
    "higherOrderRxs" - {
      val a = Var(1)
      val b = Var(2)
      val c = Rx(Rx(a() + b()) -> (a() - b()))

      assert(
        a.Internal.downStream.size == 2,
        b.Internal.downStream.size == 2,
        c.now._1.now == 3,
        c.now._2 == -1
      )

      a() = 2

      assert(
        a.Internal.downStream.size == 2,
        b.Internal.downStream.size == 2,
        c.now._1.now == 4,
        c.now._2 == 0
      )

      b() = 3

      assert(
        a.Internal.downStream.size == 2,
        b.Internal.downStream.size == 2,
        c.now._1.now == 5,
        c.now._2 == -1
      )
    }
  }
}
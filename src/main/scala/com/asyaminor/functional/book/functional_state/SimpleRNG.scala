package com.asyaminor.functional.book.functional_state


trait RNG {
  def nextInt: (Int, RNG)

}

case class SimpleRNG(seed: Long) extends RNG {
  def nextInt: (Int, RNG) = {
    val newSeed = (seed * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL
    val nextRNG = SimpleRNG(newSeed)
    val n = (newSeed >>> 16).toInt
    (n, nextRNG)
  }
}

case class State[S, +A](run: S => (A,S))

object State {
  type Rand[A] = State[RNG, A]
  def unit[A](a: A): Rand[A] = State(rng => (a, rng))
  def int: Rand[Int] = State(rng => rng.nextInt)

  def get[S]: State[S, S] = State(s => (s, s))
  def set[S](s: S): State[S, Unit] = State(_ => ((), s))

  def map[S,A,B](a: Rand[A])(f: A => B): Rand[B] = State(rng => {
    val (aValue, rng2) = a.run(rng)
    (f(aValue), rng2)
  })

  def map2[A,B,C](ra: Rand[A], rb: Rand[B])(f: (A, B) => C): Rand[C] = State(rng => {
    val (a, rng2) = ra.run(rng)
    val (b, rng3) = rb.run(rng2)

    (f(a, b), rng3)
  })

  def flatMap[A,B](f: Rand[A])(g: A => Rand[B]): Rand[B] = State(rng => {
    val (a, next) = f.run(rng)
    //g(a)(next)
    g(a).run(next)
  })

  def mapF[A,B](s: Rand[A])(f: A => B): Rand[B] = flatMap(s)(a => { State(rng =>
    (f(a), rng)
  )})

  def map2F[A,B,C](ra: Rand[A], rb: Rand[B])(f: (A, B) => C): Rand[C] = flatMap(ra)(a => State{ rng =>
    val (b, next) = rb.run(rng)
    (f(a, b), next)
  })

  def nonNegativeLessThan(n: Int): Rand[Int] = flatMap(nonNegativeInt)(i => State{ rng =>
    val mod = i % n
    if (i + (n-1) - mod >= 0)
      (mod, rng)
    else nonNegativeLessThan(n).run(rng)
  })

  def both[A,B](ra: Rand[A], rb: Rand[B]): Rand[(A,B)] = map2(ra, rb)((_, _))

  val randIntDouble: Rand[(Int, Double)] = both(int, double)
  val randDoubleInt: Rand[(Double, Int)] = both(double, int)

  def nonNegativeInt: Rand[Int] = State{ rng =>
    val (i1, rngNext) = rng.nextInt

    if (i1 == Int.MinValue) (0, rngNext)
    else if (i1 < 0) (Math.abs(i1), rngNext)
    else (i1, rngNext)
  }

  def nonNegativeEven: Rand[Int] = map(nonNegativeInt)(i => i - i % 2)

  def doubleM: Rand[Double] = map(nonNegativeInt)(i => i.toDouble / Int.MaxValue)

  def double:Rand[Double] = State { rng =>
    val (nonNeg, next) = nonNegativeInt.run(rng)

    (nonNeg.toDouble / Int.MaxValue.toDouble, next)
  }

  def intDouble(rng: RNG): ((Int,Double), RNG) = {
    val (i1, r2) = rng.nextInt
    val (d1, r3) = double.run(r2)

    ((i1, d1), r3)
  }

  def doubleInt(rng: RNG): ((Double,Int), RNG) = {
    val (d1, r2) = double.run(rng)
    val (i1, r3) = r2.nextInt

    ((d1, i1), r3)
  }

  def double3(rng: RNG): ((Double,Double,Double), RNG) = {
    val (d1, r2) = double.run(rng)
    val (d2, r3) = double.run(r2)
    val (d3, r4) = double.run(r3)

    ((d1, d2, d3), r4)
  }

  def sequence[A](fs: List[Rand[A]]): Rand[List[A]] = State(rng => {
    fs.foldLeft((Nil:List[A], rng))((a, b) => {
      val rng = a._2
      val acc = a._1

      val (value, rngNext) = b.run(rng)
      (value :: acc, rngNext)
    })
  })

  def intsSeq(count: Int): Rand[List[Int]] = sequence(List.fill(count)(int))

  def ints(count: Int)(rng: RNG): (List[Int], RNG) = {
    val (generator, list) = (0 to count).foldLeft((rng, Nil:List[Int]))((tp, b) => {
      val gen = tp._1
      val (i, gNext) = gen.nextInt

      (gNext, i::tp._2)
    })

    (list, generator)
  }
}


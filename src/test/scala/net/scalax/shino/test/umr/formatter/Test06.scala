package net.scalax.shino.test.umr.formatter

import java.util.Locale

import com.github.javafaker.Faker
import net.scalax.asuna.mapper.common.annotations.RootModel
import net.scalax.shino.umr.SlickResultIO
import slick.jdbc.H2Profile.api._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.slf4j.LoggerFactory

import scala.concurrent.{duration, Await, Future}

class Test06 extends FlatSpec with Matchers with EitherValues with ScalaFutures with BeforeAndAfterAll with BeforeAndAfter {

  case class Friend(id: Long, name: String, nick1: String, nick2: String, age: Int)
  case class NickCol(nick1: String, nick2: String)

  class FriendTable(tag: slick.lifted.Tag) extends Table[Friend](tag, "firend") with SlickResultIO {
    def id   = column[Long]("id", O.AutoInc)
    def name = column[String]("name")
    @RootModel[NickCol]
    def nick = column[String]("nick").<>[NickCol](s => NickCol(s, s), r => Option(r.nick1))
    def age  = column[Int]("age")

    override def * = shino.effect(shino.singleModel[Friend](this).compile).shape
  }

  val friendTq = TableQuery[FriendTable]

  val local = new Locale("zh", "CN")
  val faker = new Faker(local)

  def await[A](f: Future[A]) = Await.result(f, duration.Duration.Inf)

  val logger = LoggerFactory.getLogger(getClass)

  val db = Database.forURL(s"jdbc:h2:mem:formatter_test06;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver", keepAliveConnection = true)

  override def beforeAll = await(db.run(friendTq.schema.create))

  val nick1   = faker.weather.description
  val nick2   = faker.weather.description
  val nick3   = faker.weather.description
  val friend1 = Friend(-1, faker.name.name, nick1, nick1, 23)
  val friend2 = Friend(-1, faker.name.name, nick2, nick2, 26)
  val friend3 = Friend(-1, faker.name.name, nick3, nick3, 20)

  before {}

  after {
    await(db.run(friendTq.delete))
  }

  "shape" should "auto map with table and case class" in {
    val insert = friendTq.returning(friendTq.map(_.id))

    val friend1DBIO = insert += friend1
    val friend2DBIO = insert += friend2
    val friend3DBIO = insert += friend3

    val insertIds = await(db.run(DBIO.sequence(List(friend1DBIO, friend2DBIO, friend3DBIO))))

    val result = await(db.run(friendTq.result))

    insertIds.size should be(3)
    insertIds.map { s =>
      (s > 0) should be(true)
    }
    result.toList.map(s => s.copy(id = -1)) should be(List(friend1, friend2, friend3))
  }

}

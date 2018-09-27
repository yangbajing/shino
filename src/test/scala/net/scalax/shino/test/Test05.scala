package net.scalax.shino.test

import java.util.Locale

import com.github.javafaker.Faker
import net.scalax.asuna.mapper.common.annotations.RootModel
import net.scalax.shino.SlickMapper
import slick.jdbc.H2Profile.api._
import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.slf4j.LoggerFactory

import scala.concurrent.{duration, Await, Future}

class Test05 extends FlatSpec with Matchers with EitherValues with ScalaFutures with BeforeAndAfterAll with BeforeAndAfter {

  case class Friend(id: Long, name: String, nick: String, age: Int)
  case class NameAndAge(name: String, age: Int)

  class FriendTable(tag: slick.lifted.Tag) extends Table[Friend](tag, "firend") with SlickMapper {
    def id       = column[Long]("id", O.AutoInc)
    def name_ext = column[String]("name")
    def nick     = column[String]("nick")
    def age_ext  = column[Int]("age")

    @RootModel[NameAndAge]
    def name_age =
      shino
        .wrap(name_ext)
        .zip(shino.wrap(age_ext))
        .map { case (name, age) => NameAndAge("user name:" + name + ", age:" + age, age) }(t => Option((t.name, t.age)))

    override def * = shino.effect(shino.singleModel[Friend](this).compile).shape
  }

  val friendTq2 = TableQuery[FriendTable]

  val local = new Locale("zh", "CN")
  val faker = new Faker(local)

  def await[A](f: Future[A]) = Await.result(f, duration.Duration.Inf)

  val logger = LoggerFactory.getLogger(getClass)

  val db = Database.forURL(s"jdbc:h2:mem:test05;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver", keepAliveConnection = true)

  override def beforeAll = {
    await(db.run(friendTq2.schema.create))
  }

  val friend1 = Friend(-1, faker.name.name, faker.weather.description, 23)
  val friend2 = Friend(-1, faker.name.name, faker.weather.description, 26)
  val friend3 = Friend(-1, faker.name.name, faker.weather.description, 20)

  before {}

  after {
    await(db.run(friendTq2.delete))
  }

  "shape" should "auto map with table and case class" in {
    val insert = friendTq2.returning(friendTq2.map(_.id))

    val friend1DBIO = insert += friend1
    val friend2DBIO = insert += friend2
    val friend3DBIO = insert += friend3

    val insertIds = await(db.run(DBIO.sequence(List(friend1DBIO, friend2DBIO, friend3DBIO))))
    val result    = await(db.run(friendTq2.result))

    insertIds.size should be(3)
    insertIds.map { s =>
      (s > 0) should be(true)
    }
    result.toList.map(s => s.copy(id = -1)) should be(List(friend1, friend2, friend3).map(s => s.copy(name = "user name:" + s.name + ", age:" + s.age)))
  }

}
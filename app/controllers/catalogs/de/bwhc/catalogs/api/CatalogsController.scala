package de.bwhc.catalogs.api



import scala.util.Try

import scala.concurrent.{
  Future,
  ExecutionContext
}

import javax.inject.Inject

import play.api.mvc.{
  Action,
  AnyContent,
  BaseController,
  ControllerComponents,
  Request,
}
import play.api.libs.json.{
  Json, JsValue, JsObject, Format
}

import cats.Functor

import de.bwhc.catalogs.icd.{
  ICD10GM, ICD10GMCatalogs, ICDO3Catalogs
}
import de.bwhc.catalogs.hgnc.HGNCCatalog
import de.bwhc.catalogs.med.MedicationCatalog

import de.bwhc.mtb.data.entry.dtos._

import de.bwhc.rest.util.SearchSet


import shapeless.{Poly1,Generic}
import shapeless.syntax._


object Catalogs
{

  lazy val icd10gm     = ICD10GMCatalogs.getInstance.get
                       
  lazy val icdO3       = ICDO3Catalogs.getInstance.get
                       
  lazy val hgnc        = HGNCCatalog.getInstance.get

  lazy val medications = MedicationCatalog.getInstance.get



  object ToJson extends Poly1
  {

    implicit def anyCase[T](
      implicit js: Format[ValueSet[T]]
    ): Case.Aux[ValueSet[T],JsValue] = 
      at(Json.toJson(_))

  }


  implicit class ProductOps[T <: Product](val t: T) extends AnyVal
  {
    def toHList[L](
      implicit gen: Generic.Aux[T,L]
    ): L = gen.to(t)
  }


  lazy val jsonValueSets =
    ValueSets.all
      .toHList
      .map(ToJson)
      .toList
      .map(js => (js \ "name").as[String].toLowerCase -> js)
      .toMap


}


import de.bwhc.rest.util.cphl._
import de.bwhc.rest.util.cphl.syntax._
import de.bwhc.rest.util.cphl.Relations._
import de.bwhc.rest.util.cphl.Method._

trait CatalogHypermedia
{

  val baseUrl = "/bwhc/catalogs/api"

  private val links =
    (Base -> Action(s"$baseUrl/", GET)) +:
    (
      Seq("icd-10-gm","icd-o-3-t","icd-o-3-m","hgnc","atc")
        .map(sys => Relation(s"catalog-$sys") -> Action(s"$baseUrl/Coding?system=$sys" , GET)) ++
      (

        (Relation("valuesets") -> Action(s"$baseUrl/ValueSet" , GET)) +:

        Catalogs.jsonValueSets.keys 
          .map(vs => Relation(s"valueset-$vs") -> Action(s"$baseUrl/ValueSet?name=$vs" , GET)).toSeq
      )
    )

  val apiCPHL =
    CPHL(links: _*)
//    CPHL.empty[JsObject](links: _*)

}


class CatalogsController @Inject()(
  val controllerComponents: ControllerComponents
)(
  implicit ec: ExecutionContext
)
extends BaseController
with CatalogHypermedia
{

  import Catalogs._ 


  def apiHypermedia: Action[AnyContent] =
    Action {
      Ok(Json.toJson(apiCPHL))
    }


  def coding(
    system: String,
    pattern: Option[String],
    version: Option[String]
  ): Action[AnyContent] = {

    Action {
      
      val result = 
        system.toLowerCase match {

          case "icd-10-gm" => {
            (
              version match {

                case Some(vsn) => Try { ICD10GM.Version(vsn) }
                                    .map(v => pattern.fold(icd10gm.codings())(icd10gm.matches(_,v)) )

                case None    => Try { pattern.fold(icd10gm.codings())(icd10gm.matches(_)) }

              }
            )
            .map(SearchSet(_))
            .map(Json.toJson(_))
          }


          case "icd-o-3-t" => Try {
                                pattern.fold(icdO3.topographyCodings())(icdO3.topographyMatches(_))
                              }
                              .map(SearchSet(_))
                              .map(Json.toJson(_))

          case "icd-o-3-m" => Try {
                                pattern.fold(icdO3.morphologyCodings())(icdO3.morphologyMatches(_))
                              }
                              .map(SearchSet(_))
                              .map(Json.toJson(_))

          case "hgnc"      => Try {
                                pattern.fold(hgnc.genes)(hgnc.genesMatchingName(_))
                              }
                              .map(SearchSet(_))
                              .map(Json.toJson(_))

          case "atc"       => Try {
                                pattern.map(medications.findMatching(_)).getOrElse(medications.entries)
                              }
                              .map(SearchSet(_))
                              .map(Json.toJson(_))

          case _           => Try { throw new IllegalArgumentException(s"Unknown Coding $system") }

        }

      result.fold(
        t => NotFound(s"Unknown Coding System '$system' or wrong/unavailable version '$version'"),
        Ok(_)
      )

    }

  }



  def valueSets: Action[AnyContent] =
    Action {
      Ok(Json.toJson(SearchSet(jsonValueSets.values)))
    }



  def valueSet(
    name: String
  ): Action[AnyContent] = 
    Action {

     jsonValueSets.get(name.toLowerCase)
       .map(Ok(_))
       .getOrElse(NotFound(s"Unknown ValueSet $name"))

   }


}

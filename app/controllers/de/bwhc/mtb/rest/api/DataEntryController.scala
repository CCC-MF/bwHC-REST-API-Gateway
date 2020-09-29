package de.bwhc.mtb.rest.api



import scala.util.{
  Left,
  Right
}
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
  Result
}
import play.api.libs.json.Json.toJson

import cats.syntax.either._

import de.bwhc.mtb.data.entry.dtos.{
  MTBFile,
  Patient
}
import de.bwhc.mtb.data.entry.api.MTBDataService

import de.bwhc.rest.auth.{AuthorizationActions,UserRequest,UserAction}
import de.bwhc.rest.util.SearchSet



class DataEntryController @Inject()(
  val controllerComponents: ControllerComponents,
  val services: Services,
  val userAction: UserAction
)(
  implicit ec: ExecutionContext
)
extends RequestOps
with AuthorizationActions
{


  import MTBDataService.Command._
  import MTBDataService.Response._
  import MTBDataService.Error._


  private val service = services.dataService


  def processUpload: Action[AnyContent] =
    Action.async {

      implicit req =>

      processJson[MTBFile]{ mtbfile =>

        (service ! Upload(mtbfile))
          .map(
            _.fold(
              {
                case InvalidData(qc) =>
                  UnprocessableEntity(toJson(qc))

                case UnspecificError(msg) =>
                  BadRequest(toJson(Outcome.fromErrors(List(msg))))
              },
              {
                case Imported(input,_) =>
                  Ok(toJson(input))
                    .withHeaders(LOCATION -> s"/data/MTBFile/${mtbfile.patient.id.value}")

                case IssuesDetected(qc,_) => 
                  Created(toJson(qc))
                    .withHeaders(LOCATION -> s"/data/DataQualityReport/${mtbfile.patient.id.value}")

                case _ => InternalServerError
              }
            )
          )
      }

   }


  def patients: Action[AnyContent] =
    Action.async {
      for {
        ps   <- service.patientsWithIncompleteData 
        set  =  SearchSet(ps)
        json =  toJson(set)   
      } yield Ok(json)
    }


/*
  import de.bwhc.user.auth.api.Role._

  def patients: Action[AnyContent] =
    userAction
      .andThen(
        PermissionCheck[UserRequest](
          _.user match {
            case Some(user) if (!user.roles.isEmpty) => true
            case _ => false
          }
        )
      )
      .async {
        for {
          ps   <- service.patientsWithIncompleteData 
          set  =  SearchSet(ps)
          json =  toJson(set)   
        } yield Ok(json)
      }
*/


  def mtbfile(id: String): Action[AnyContent] =
    Action.async {
      (service mtbfile Patient.Id(id))
        .map (_ toJsonOrElse (s"Invalid Patient ID $id"))
    }


  def dataQualityReport(id: String): Action[AnyContent] =
    Action.async {
      (service dataQualityReport Patient.Id(id))
        .map (_ toJsonOrElse (s"Invalid Patient ID $id"))
    }


  def delete(id: String): Action[AnyContent] =
    Action.async {

      (service ! Delete(Patient.Id(id)))
        .map(
          _.fold(
            {
              case UnspecificError(msg) =>
                BadRequest(toJson(Outcome.fromErrors(List(msg))))

              case _ => InternalServerError
            },
            {
              case Deleted(_,_) => Ok

              case _ => InternalServerError
            }
          )
        ) 
    }



}

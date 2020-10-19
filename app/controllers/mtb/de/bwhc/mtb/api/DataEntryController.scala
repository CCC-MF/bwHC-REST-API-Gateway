package de.bwhc.mtb.api



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
  BodyParsers,
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

import de.bwhc.rest.util.{Outcome,RequestOps,SearchSet}

import de.bwhc.auth.core._
import de.bwhc.auth.api._
import de.bwhc.services.{WrappedDataService, WrappedSessionManager}



object DataQualityModePermissions
{

  import de.bwhc.user.api.Role._

  private val DocumentaristRights =
    Authorization[UserWithRoles](_ hasRole Documentarist)


  val DataQualityAccessRights = DocumentaristRights


}




class DataEntryController @Inject()(
  val controllerComponents: ControllerComponents,
  val dataService: WrappedDataService,
  val sessionManager: WrappedSessionManager
)(
  implicit ec: ExecutionContext
)
extends BaseController
with RequestOps
with AuthenticationOps[UserWithRoles]
{

  import DataQualityModePermissions._

  import MTBDataService.Command._
  import MTBDataService.Response._
  import MTBDataService.Error._

  implicit val authService = sessionManager.instance

  private val service = dataService.instance


  def patients: Action[AnyContent] =
    AuthenticatedAction(DataQualityAccessRights).async {
      for {
        ps   <- service.patientsWithIncompleteData 
        set  =  SearchSet(ps)
        json =  toJson(set)   
      } yield Ok(json)
    }



  def mtbfile(id: String): Action[AnyContent] =
    AuthenticatedAction(DataQualityAccessRights).async {

      service.mtbfile(Patient.Id(id))
        .map(_ toJsonOrElse (s"Invalid Patient ID $id"))

    }


  def dataQualityReport(id: String): Action[AnyContent] =
    AuthenticatedAction(DataQualityAccessRights).async {

      service.dataQualityReport(Patient.Id(id))
        .map(_ toJsonOrElse (s"Invalid Patient ID $id"))

    }


  def delete(id: String): Action[AnyContent] =
    AuthenticatedAction(DataQualityAccessRights).async {

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

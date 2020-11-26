package de.bwhc.systems.api



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
import play.api.libs.json.{
  Json, Format
}

import de.bwhc.mtb.data.entry.dtos.{
  MTBFile,
  Patient,
  ZPM
}
import de.bwhc.mtb.data.entry.dtos.Patient
import de.bwhc.mtb.data.entry.api.MTBDataService
import de.bwhc.mtb.query.api._


import cats.data.{
  EitherT,
  OptionT
}
import cats.instances.future._
import cats.syntax.either._

import de.bwhc.rest.util.{Outcome,RequestOps,SearchSet}

import de.bwhc.services.{WrappedDataService,WrappedQueryService}


class SystemAgentController @Inject()(
  val controllerComponents: ControllerComponents,
  val dataService: WrappedDataService,
  val queryService: WrappedQueryService
)(
  implicit ec: ExecutionContext
)
extends BaseController
with RequestOps
{


  import Json.toJson

  import MTBDataService.Command._
  import MTBDataService.Response._
  import MTBDataService.Error._


  //---------------------------------------------------------------------------
  // Data Import
  //---------------------------------------------------------------------------

  def processUpload: Action[AnyContent] =
    JsonAction[MTBFile]{ mtbfile =>

      (dataService.instance ! Upload(mtbfile))
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
//                  .withHeaders(LOCATION -> s"/data/MTBFile/${mtbfile.patient.id.value}")

              case IssuesDetected(qc,_) => 
                Created(toJson(qc))
//                  .withHeaders(LOCATION -> s"/data/DataQualityReport/${mtbfile.patient.id.value}")

              case _ => InternalServerError
            }
          )
        )
    }


  def delete(patId: String): Action[AnyContent] = {
     Action.async {
       (dataService.instance ! Delete(Patient.Id(patId)))
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


  //---------------------------------------------------------------------------
  // Peer-to-peer operations
  //---------------------------------------------------------------------------

  private val BWHC_SITE_ORIGIN  = "bwhc-site-origin"
  private val BWHC_QUERY_USERID = "bwhc-query-userid"


  def getLocalQCReport: Action[AnyContent] = 
    Action.async {

      request =>

      //TODO: get originating ZPM and Querier from request
      
//      val querier = Querier("TODO")
//      val origin  = ZPM("TODO")

      val result =
        for {
          origin  <- request.headers.get(BWHC_SITE_ORIGIN).map(ZPM(_))
          querier <- request.headers.get(BWHC_QUERY_USERID).map(Querier(_))
          res =
            for {
              qc      <- queryService.instance.getLocalQCReportFor(origin,querier)
              outcome =  qc.leftMap(List(_))
                           .leftMap(Outcome.fromErrors)
            } yield outcome.toJsonResult
        
        } yield res

      result.getOrElse(
        Future.successful(BadRequest(s"Missing Header(s): $BWHC_SITE_ORIGIN and/or $BWHC_QUERY_USERID"))
      )

    }
 

  def processPeerToPeerQuery: Action[AnyContent] = 
    JsonAction[PeerToPeerQuery]{
      query =>
        queryService.instance.resultsOf(query)
          .map(SearchSet(_))
          .map(Json.toJson(_))
          .map(Ok(_))
    }

}

package de.bwhc.mtb.rest.api



import scala.concurrent.{
  ExecutionContext,
  Future
}

import play.api.mvc.{
  Action,
//  ActionRefiner,
  AnyContent,
  BaseController,
  Request,
  Result
}

import play.api.libs.json._
import Json.toJson

import cats.data.Ior
import cats.syntax.either._


abstract class RequestOps extends BaseController
{

/*
  def JsonAction[T: Reads](
    block: T => Future[Result]  
  )(
    implicit ec: ExecutionContext 
  ) = ActionRefiner[Request[AnyContent],Option[T]]{

    override def refine[A](request: R[A]): Future[Either[Result, P[A]]] = ???

  }
*/


  def processJson[T: Reads](
    f: T => Future[Result]
  )(
    implicit
    req: Request[AnyContent],
    ec: ExecutionContext
  ): Future[Result] = {

    req.body
      .asJson
      .fold(
        Future.successful(BadRequest("Missing or Invalid JSON body"))
      )(
        _.validate[T]
         .asEither
         .leftMap(Outcome.fromJsErrors(_))
         .leftMap(toJson(_))
         .fold(
           out => Future.successful(BadRequest(out)),
           f(_)
         )
      )

  }


  implicit class OptionOps[T: Writes](opt: Option[T])
  {

    def toJsonOrElse(err: => String) = 
      opt.map(toJson(_))
        .map(Ok(_))
        .getOrElse(NotFound(err)) 

  }



  implicit class EitherOutcomeOps[T](xor: Either[Outcome,T])(
    implicit
    we: Writes[Outcome],
    wt: Writes[T]
  ){

    def toJsonResult: Result = {
       xor.bimap(
        toJson(_),
        toJson(_)
      )
      .fold(
        out => InternalServerError(out),
        Ok(_)
      )
     }

  }


  implicit class IorOutcomeOps[T](ior: Ior[Outcome,T])(
    implicit
    we: Writes[Outcome],
    wt: Writes[T]
  ){

    def toJsonResult: Result = {
       ior.bimap(
        toJson(_),
        toJson(_)
      )
      .fold(
        out => InternalServerError(out),
        Ok(_),
        (out,_) => InternalServerError(out),
      )

     }

  }


}


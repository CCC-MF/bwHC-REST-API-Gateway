package de.bwhc.mtb.rest.api



import javax.inject.Inject

import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._


class Router @Inject()(
  examples: ExampleProvider,
  dataEntry: DataEntryController,
  querying: QueryController
)
extends SimpleRouter
{

  override def routes: Routes = {

    //-------------------------------------------------------------------------
    // Data example endpoints
    //-------------------------------------------------------------------------
    case GET(p"/data/examples/MTBFile")             => examples.mtbfile


    //-------------------------------------------------------------------------
    // Data Management endpoints
    //-------------------------------------------------------------------------

    case POST(p"/data/MTBFile")                     => dataEntry.processUpload

    case GET(p"/data/Patient")                      => dataEntry.patients
    case GET(p"/data/MTBFile/$id")                  => dataEntry.mtbfile(id)
    case GET(p"/data/DataQualityReport/$id")        => dataEntry.dataQualityReport(id)

    case DELETE(p"/data/Patient/$id")               => dataEntry.delete(id)


    //-------------------------------------------------------------------------
    // ZPM QC Reports
    //-------------------------------------------------------------------------
    case GET(p"/reporting/LocalQCReport")           => querying.getLocalQCReport
    case GET(p"/reporting/GlobalQCReport")          => querying.getGlobalQCReport


    //-------------------------------------------------------------------------
    // MTBFile Cohort Queries
    //-------------------------------------------------------------------------
 
    case POST(p"/query")                            => querying.submit
    case POST(p"/query/$id")                        => querying.update(id)
    case PUT(p"/query/$id/filter")                  => querying.applyFilter(id)

    case GET(p"/query/$id")                         => querying.query(id)
    case GET(p"/query/$id/Patient")                 => querying.patientsFrom(id)
    case GET(p"/query/$id/MTBFile/$patId")          => querying.mtbfileFrom(id,patId)


  }


}
